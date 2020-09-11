package io.github.tetherlessworld.mcsapps.lib.kg.stores.neo4j

import com.google.inject.Inject
import io.github.tetherlessworld.mcsapps.lib.kg.formats.kgtk.KgtkEdgeWithNodes
import io.github.tetherlessworld.mcsapps.lib.kg.models.kg.{KgEdge, KgNode, KgPath, KgSource}
import io.github.tetherlessworld.mcsapps.lib.kg.stores.{KgCommandStore, KgCommandStoreTransaction, Neo4jStoreConfiguration}
import javax.inject.Singleton
import org.neo4j.driver._
import org.slf4j.LoggerFactory

import scala.collection.mutable


@Singleton
final class Neo4jKgCommandStore @Inject()(configuration: Neo4jStoreConfiguration) extends AbstractNeo4jKgStore(configuration) with KgCommandStore {
  private var bootstrapped: Boolean = false
  private val logger = LoggerFactory.getLogger(getClass)

  private final class Neo4jKgCommandStoreTransaction extends KgCommandStoreTransaction {
      private final implicit class TransactionWrapper(transaction: Transaction) extends KgCommandStoreTransaction {
        final def clear(): Unit =
        // https://neo4j.com/developer/kb/large-delete-transaction-best-practices-in-neo4j/
          transaction.run(
            """CALL apoc.periodic.iterate("MATCH (n) return n", "DETACH DELETE n", {batchSize:1000})
              |YIELD batches, total
              |RETURN batches, total
              |""".stripMargin)

        override def close(): Unit = {}

        final def putEdge(edge: KgEdge) =
          transaction.run(
            s"""MATCH (subject:${NodeLabel} {id: $$subject}), (object:${NodeLabel} {id: $$object})
              |CALL apoc.create.relationship(subject, $$predicate, {id: $$id, labels: $$labels, sentences: $$sentences, sources: $$sources}, object) YIELD rel
              |REMOVE rel.noOp
              |""".stripMargin,
            toTransactionRunParameters(Map(
              "id" -> edge.id,
              "labels" -> edge.labels.mkString(ListDelimString),
              "object" -> edge.`object`,
              "predicate" -> edge.predicate,
              "sentences" -> edge.sentences.mkString(ListDelimString),
              "sources" -> edge.sourceIds.mkString(ListDelimString),
              "subject" -> edge.subject,
            ))
          )

        final override def putEdges(edges: Iterator[KgEdge]): Unit = {
          // The putEdges, putNodes, et al. methods assume that the iterator is small enough to buffer.
          // We buffer here, but the transaction also buffers.
          val edgesList = edges.toList
          putSources(edgesList.flatMap(_.sourceIds).distinct.map(KgSource(_)))
          for (edge <- edgesList) {
            putEdge(edge)
          }
        }

        final override def putKgtkEdgesWithNodes(edgesWithNodes: Iterator[KgtkEdgeWithNodes]): Unit = {
          val edgesWithNodesList = edgesWithNodes.toList
          // Neo4j doesn't tolerate duplicate nodes
          val putNodeIds = new mutable.HashSet[String]
          putSources(edgesWithNodesList.flatMap(_.sources).distinct.map(KgSource(_)))
          for (edgeWithNodes <- edgesWithNodesList) {
            if (putNodeIds.add(edgeWithNodes.node1.id)) {
              putNode(edgeWithNodes.node1)
            }
            if (putNodeIds.add(edgeWithNodes.node2.id)) {
              putNode(edgeWithNodes.node2)
            }
            putEdge(edgeWithNodes.edge)
          }
        }

        final def putNode(node: KgNode): Unit = {
          transaction.run(
            s"CREATE (:${NodeLabel} { id: $$id, labels: $$labels, pos: $$pos, sources: $$sources });",
            toTransactionRunParameters(Map(
              "id" -> node.id,
              "labels" -> node.labels.mkString(ListDelimString),
              "pos" -> node.pos.getOrElse(null),
              "sources" -> node.sourceIds.mkString(ListDelimString),
            ))
          )
          // Store sources as a delimited list on the node so they can be retrieved accurately
          // In order to do filtering on individual sources we need to break them out as nodes.
          // This is the standard way of doing multi-valued properties in neo4j: make the values
          // separate nodes and connect to them.
          // See #168.
          for (sourceId <- node.sourceIds) {
            transaction.run(
              s"""
                 |MATCH (source:${SourceLabel}), (node:${NodeLabel})
                 |WHERE node.id = $$nodeId AND source.id = $$sourceId
                 |CREATE (node)-[:${SourceRelationshipType}]->(source)
                 |""".stripMargin,
              toTransactionRunParameters(Map(
                "nodeId" -> node.id,
                "sourceId" -> sourceId
              ))
            )
          }

          for (label <- node.labels) {
            transaction.run(
              s"""
                 |MATCH (node:${NodeLabel} {id:$$nodeId})
                 |MERGE (label:${LabelLabel} {id:$$labelId, label:$$labelId, labels:$$labelId})
                 |MERGE (node)-[:${LabelRelationshipType}]->(label)
                 |""".stripMargin,
              toTransactionRunParameters(Map(
                "labelId" -> label,
                "nodeId" -> node.id
              ))
            )
          }

          transaction.run(
            s"""
               |MATCH (node:${NodeLabel} {id:$$nodeId})
               |MATCH (s:${LabelLabel})<-[:${LabelRelationshipType}]-(node)-[:${LabelRelationshipType}]->(t:${LabelLabel})
               |MERGE (s)-[:${LabelEdgeRelationshipType}]-(t)
               |""".stripMargin,
            toTransactionRunParameters(Map(
              "nodeId" -> node.id
            ))
          )
        }

        final override def putNodes(nodes: Iterator[KgNode]): Unit = {
          val nodesList = nodes.toList
          putSources(nodesList.flatMap(_.sourceIds).distinct.map(KgSource(_)))
          for (node <- nodesList) {
            putNode(node)
          }
        }

        final override def putPaths(paths: Iterator[KgPath]): Unit =
          for (path <- paths) {
            for (pathEdgeWithIndex <- path.edges.zipWithIndex) {
              val (pathEdge, pathEdgeIndex) = pathEdgeWithIndex
              transaction.run(
                s"""
                   |MATCH (subject:${NodeLabel}), (object: ${NodeLabel})
                   |WHERE subject.id = $$subject AND object.id = $$object
                   |CREATE (subject)-[path:${PathRelationshipType} {id: $$pathId, pathEdgeIndex: $$pathEdgeIndex, pathEdgePredicate: $$pathEdgePredicate, sources: $$sources}]->(object)
                   |""".stripMargin,
                toTransactionRunParameters(Map(
                  "object" -> pathEdge.`object`,
                  "pathEdgeIndex" -> pathEdgeIndex,
                  "pathEdgePredicate" -> pathEdge.predicate,
                  "pathId" -> path.id,
                  "sources" -> path.sourceIds.mkString(ListDelimString),
                  "subject" -> pathEdge.subject
                ))
              )
            }
          }

        final override def putSources(sources: Iterator[KgSource]): Unit =
          for (source <- sources) {
            transaction.run(
              s"""
                 |MERGE (:${SourceLabel} { id: $$id, label: $$label, labels: $$label, sources: $$id })
                 |""".stripMargin,
              toTransactionRunParameters(Map(
                "id" -> source.id,
                "label" -> source.label
              ))
            )
          }

        final def writeLabelPageRanks: Unit = {
          if (!transaction.run(s"MATCH (n: ${LabelLabel}) RETURN n LIMIT 1").hasNext) {
            return
          }

          transaction.run(
            s"""
               |CALL gds.pageRank.write({
               |nodeQuery: 'MATCH (n: ${LabelLabel}) RETURN id(n) as id',
               |relationshipQuery: 'MATCH (s: ${LabelLabel})-[:${LabelEdgeRelationshipType}]-(t: ${LabelLabel}) RETURN id(s) as source, id(t) as target',
               |writeProperty: 'pageRank'
               |})
               |""".stripMargin
          )
        }

        final def writeLabelSources: Unit = {
          if (!transaction.run(s"MATCH (n: ${LabelLabel}) RETURN n LIMIT 1").hasNext) {
            return
          }

          transaction.run(
            s"""
               |MATCH (label:${LabelLabel})<-[:${LabelRelationshipType}]-(:${NodeLabel})-[:${SourceRelationshipType}]->(source:${SourceLabel})
               |WITH label, collect(distinct source.id) AS sourceIds
               |SET label.sources = apoc.text.join(sourceIds, $$listDelim)
               |""".stripMargin,
            toTransactionRunParameters(Map(
              "listDelim" -> ListDelimString
            ))
          )
        }

        final def writeNodePageRanks: Unit = {
          if (!transaction.run(s"MATCH (n: ${NodeLabel}) RETURN n LIMIT 1").hasNext) {
            return
          }

          transaction.run(
            s"""
               |CALL gds.pageRank.write({
               |nodeQuery: 'MATCH (n: ${NodeLabel}) RETURN id(n) as id',
               |relationshipQuery: 'MATCH (source: ${NodeLabel})-[r]->(target: ${NodeLabel}) WHERE TYPE(r)<>"${PathRelationshipType}" AND TYPE(r)<>"${LabelRelationshipType}" RETURN id(source) as source, id(target) as target',
               |writeProperty: 'pageRank'
               |})
               |""".stripMargin
          )
        }
      }

    override final def clear(): Unit = {
      // It would be simpler to use CREATE OR REPLACE DATABASE, but the free Neo4j 4.0 Community Edition doesn't support it,
      // and the open source fork of the Neo4j Enterprise Edition doesn't include 4.0 features yet.
      withWriteTransaction { transaction =>
        transaction.clear()
        transaction.commit()
      }
      withReadTransaction { transaction =>
        while (transaction.run("MATCH (n) RETURN COUNT(n) as count").single().get("count").asInt() != 0) {
          logger.info("waiting for neo4j to clear")
          Thread.sleep(10)
        }
      }
    }

    final override def close(): Unit = {
      writeNodePageRanks
      writeLabelPageRanks
      writeLabelSources
    }

    final override def putEdges(edges: Iterator[KgEdge]): Unit =
      putModelsBatched(edges) { (edges, transaction) => {
        transaction.putEdges(edges)
      }
      }

    final override def putKgtkEdgesWithNodes(edgesWithNodes: Iterator[KgtkEdgeWithNodes]): Unit = {
      // Neo4j doesn't tolerate duplicate nodes
      val putNodeIds = new mutable.HashSet[String]
      putModelsBatched(edgesWithNodes) { (edgesWithNodes, transaction) => {
        putSources(edgesWithNodes.flatMap(_.sources).distinct.map(KgSource(_)))
        for (edgeWithNodes <- edgesWithNodes) {
          if (putNodeIds.add(edgeWithNodes.node1.id)) {
            transaction.putNode(edgeWithNodes.node1)
          }
          if (putNodeIds.add(edgeWithNodes.node2.id)) {
            transaction.putNode(edgeWithNodes.node2)
          }
          transaction.putEdge(edgeWithNodes.edge)
        }
      }
      }
    }

    private def putModelsBatched[ModelT](models: Iterator[ModelT])(putModelBatch: (List[ModelT], Transaction) => Unit): Unit = {
      // Batch the models in order to put them all in a transaction.
      // My (MG) first implementation looked like:
      //      for (modelWithIndex <- models.zipWithIndex) {
      //        val (model, modelIndex) = modelWithIndex
      //        putModel(transaction, model)
      //        if (modelIndex > 0 && (modelIndex + 1) % PutCommitInterval == 0) {
      //          tryOperation(() => transaction.commit())
      //          transaction = session.beginTransaction()
      //        }
      //      }
      // tryOperation handled TransientException, but the first transaction always failed and was rolled back.
      // I don't have time to investigate that. Batching models should be OK for now.
      val modelBatch = new mutable.MutableList[ModelT]
      while (models.hasNext) {
        while (modelBatch.size < configuration.commitInterval && models.hasNext) {
          modelBatch += models.next()
        }
        if (!modelBatch.isEmpty) {
          //          logger.info("putting batch of {} models in a transaction", modelBatch.size)
          withWriteTransaction { transaction =>
            putModelBatch(modelBatch.toList, transaction)
            transaction.commit()
          }
          modelBatch.clear()
        }
      }
    }

    final override def putNodes(nodes: Iterator[KgNode]): Unit =
      putModelsBatched(nodes) { (nodes, transaction) => {
        transaction.putNodes(nodes)
      }
      }

    final override def putPaths(paths: Iterator[KgPath]): Unit =
      putModelsBatched(paths) { (paths, transaction) => {
        transaction.putPaths(paths)
      }
      }

    final override def putSources(sources: Iterator[KgSource]): Unit =
      withWriteTransaction { transaction =>
        transaction.putSources(sources)
        transaction.commit()
      }

    private def writeLabelPageRanks: Unit = {
      withWriteTransaction { transaction =>
        transaction.writeLabelPageRanks
        transaction.commit()
      }
    }

    private def writeLabelSources: Unit = {
      withWriteTransaction { transaction =>
        transaction.writeLabelSources
        transaction.commit()
      }
    }

    private def writeNodePageRanks: Unit = {
      withWriteTransaction { transaction =>
        transaction.writeNodePageRanks
        transaction.commit()
      }
    }
  }

  bootstrapStore()

  private def bootstrapStore(): Unit = {
    this.synchronized {
      if (bootstrapped) {
        return
      }

      withSession { session =>
        val hasConstraints =
          session.readTransaction { transaction =>
            val result =
              transaction.run("CALL db.constraints")
            result.hasNext
          }

        if (hasConstraints) {
          logger.info("neo4j indices already exist")
          bootstrapped = true
          return
        }

        logger.info("bootstrapping neo4j indices")

        val bootstrapCypherStatements = List(
          s"""CALL db.index.fulltext.createNodeIndex("node",["${NodeLabel}", "${SourceLabel}", "${LabelLabel}"],["id", "label", "labels", "sources"]);""",
          s"""CREATE CONSTRAINT node_id_constraint ON (node:${NodeLabel}) ASSERT node.id IS UNIQUE;""",
          s"""CREATE CONSTRAINT source_id_constraint ON (source:${SourceLabel}) ASSERT source.id IS UNIQUE;""",
          s"""CREATE CONSTRAINT label_id_constraint ON (label:${LabelLabel}) ASSERT label.id IS UNIQUE;"""
        )

        session.writeTransaction { transaction =>
          for (bootstrapCypherStatement <- bootstrapCypherStatements) {
            transaction.run(bootstrapCypherStatement)
          }
          transaction.commit()
        }
      }

      logger.info("bootstrapped neo4j indices")
    }
  }

  override def beginTransaction: KgCommandStoreTransaction =
    new Neo4jKgCommandStoreTransaction

  private def withWriteTransaction[V](f: Transaction => V): V =
    withSession { session => {
      session.writeTransaction { transaction =>
        f(transaction)
      }
    }
    }
}


