package io.github.tetherlessworld.mcsapps.lib.kg.stores.neo4j

import com.google.inject.Inject
import io.github.tetherlessworld.mcsapps.lib.kg.models.kg.{KgEdge, KgNode, KgPath, KgSource}
import io.github.tetherlessworld.mcsapps.lib.kg.stores._
import javax.inject.Singleton
import org.neo4j.driver._

import scala.collection.JavaConverters._
import scala.util.Try

@Singleton
final class Neo4jKgQueryStore @Inject()(configuration: Neo4jStoreConfiguration) extends AbstractNeo4jKgStore(configuration) with KgQueryStore {

  private final implicit class RecordWrapper(record: Record) {
    private def toList(value: String): List[String] = {
      if (value.isEmpty) {
        List()
      } else {
        value.split(ListDelimChar).toList
      }
    }

    def toEdge: KgEdge = {
      val recordMap = record.asMap().asScala.toMap
      KgEdge(
        id = recordMap("edge.id").asInstanceOf[String],
        labels = toList(recordMap("edge.labels").asInstanceOf[String]),
        `object` = recordMap("object.id").asInstanceOf[String],
        predicate = recordMap("type(edge)").asInstanceOf[String],
        sentences = toList(recordMap("edge.sentences").asInstanceOf[String]),
        sourceIds = toList(recordMap("edge.sources").asInstanceOf[String]),
        subject = recordMap("subject.id").asInstanceOf[String]
      )
    }

    def toNode: KgNode = {
      val recordMap = record.asMap().asScala.toMap
      KgNode(
        id = recordMap("node.id").asInstanceOf[String],
        labels = toList(recordMap("node.labels").asInstanceOf[String]),
        pageRank = Try(recordMap("node.pageRank").asInstanceOf[Double].doubleValue()).toOption,
        pos = Option(recordMap("node.pos")).map(_.asInstanceOf[String]),
        sourceIds = toList(recordMap("node.sources").asInstanceOf[String])
      )
    }

    def toPathRecord: Neo4jKgPathRecord = {
      Neo4jKgPathRecord(
        objectNodeId = record.get("objectNode.id").asString(),
        pathId = record.get("path.id").asString(),
        pathEdgeIndex = record.get("path.pathEdgeIndex").asInt(),
        pathEdgePredicate = record.get("path.pathEdgePredicate").asString(),
        sources = record.get("path.sources").asString().split(ListDelimChar).toList,
        subjectNodeId = record.get("subjectNode.id").asString()
      )
    }

    def toSource: KgSource = {
      val recordMap = record.asMap().asScala.toMap
      KgSource(
        id = recordMap("source.id").asInstanceOf[String],
        label = recordMap("source.label").asInstanceOf[String]
      )
    }
  }

  private final implicit class ResultWrapper(result: Result) {
    def toEdges: List[KgEdge] =
      result.asScala.toList.map(record => record.toEdge)

    def toNodes: List[KgNode] =
      result.asScala.toList.map(record => record.toNode)

    def toPaths: List[KgPath] = {
      result.asScala.toList.map(record => record.toPathRecord).groupBy(pathRecord => pathRecord.pathId).map(pathRecordsEntry =>
        pathRecordsEntry match {
          case (pathId, pathRecords) =>
            KgPath(
              edges = pathRecords.sortBy(pathRecord => pathRecord.pathEdgeIndex).map(pathRecord => pathRecord.toEdge),
              id = pathId,
               sourceIds = pathRecords(0).sources,
            )
        }
      ).toList
    }
  }

  private final implicit class TransactionWrapper(transaction: Transaction) extends KgQueryStore {
    private val edgePropertyNameList = List("id", "labels", "object", "sentences", "sources", "subject")
    private val edgePropertyNamesString = edgePropertyNameList.map(edgePropertyName => "edge." + edgePropertyName).mkString(", ")
    private val nodePropertyNameList = List("id", "labels", "pos", "sources", "pageRank")
    private val nodePropertyNamesString = nodePropertyNameList.map(nodePropertyName => "node." + nodePropertyName).mkString(", ")
    private val pathPropertyNameList = List("id", "objectNode", "pathEdgeIndex", "pathEdgePredicate", "sources", "subjectNode")
    private val pathPropertyNamesString = pathPropertyNameList.map(pathPropertyName => "path." + pathPropertyName).mkString(", ")

    final def clear(): Unit =
    // https://neo4j.com/developer/kb/large-delete-transaction-best-practices-in-neo4j/
      transaction.run(
        """CALL apoc.periodic.iterate("MATCH (n) return n", "DETACH DELETE n", {batchSize:1000})
          |YIELD batches, total
          |RETURN batches, total
          |""".stripMargin)

    final override def getEdges(filters: KgEdgeFilters, limit: Int, offset: Int, sort: KgEdgesSort) = {
      val cypher = filterEdgesCypher(filters)
      val sortFieldCypher = sort.field match {
        case KgEdgesSortField.Id => "edge.id"
        case KgEdgesSortField.ObjectPageRank => "object.pageRank"
      }

      transaction.run(
        s"""
           |${cypher}
           |RETURN type(edge), object.id, subject.id, ${edgePropertyNamesString}
           |ORDER BY ${sortFieldCypher} ${if (sort.direction == SortDirection.Ascending) "asc" else "desc"}
           |SKIP ${offset}
           |LIMIT ${limit}
           |""".stripMargin,
        toTransactionRunParameters(caseClassToMap(filters))
      ).toEdges
    }

    final override def getSourcesById: Map[String, KgSource] =
      transaction.run(s"MATCH (source:${SourceLabel}) RETURN source.id, source.label").asScala.map(record =>
        KgSource(
          id = record.get("source.id").asString(),
          label = record.get("source.label").asString()
        )
      ).map(source => (source.id, source)).toMap

    final override def getNodeById(id: String): Option[KgNode] =
      transaction.run(
        s"MATCH (node:${NodeLabel} {id: $$id}) RETURN ${nodePropertyNamesString};",
        toTransactionRunParameters(Map("id" -> id))
      ).toNodes.headOption

    override def getNodesByLabel(label: String): List[KgNode] =
      transaction.run(
        s"""
           |MATCH (label:${LabelLabel} {id: $$label})<-[:${LabelRelationshipType}]-(node:${NodeLabel})
           |RETURN ${nodePropertyNamesString}
           |""".stripMargin,
        toTransactionRunParameters(Map("label" -> label))
      ).toNodes

    final override def getPathById(id: String): Option[KgPath] =
      transaction.run(
        s"""MATCH (subjectNode:${NodeLabel})-[path:${PathRelationshipType} {id: $$id}]->(objectNode:${NodeLabel})
           |RETURN objectNode.id, subjectNode.id, ${pathPropertyNamesString}
           |""".stripMargin,
        toTransactionRunParameters(Map("id" -> id))
      ).toPaths.headOption

    final override def getRandomNode: KgNode =
      transaction.run(
        s"MATCH (node:${NodeLabel}) RETURN ${nodePropertyNamesString}, rand() as rand ORDER BY rand ASC LIMIT 1"
      ).toNodes.head

    override def getTopEdges(filters: KgEdgeFilters, limit: Int, sort: KgTopEdgesSort): List[KgEdge] = {
      val edgeCypher = filterEdgesCypher(filters)

      val cypher = sort.field match {
        case KgTopEdgesSortField.ObjectLabelPageRank =>
          s"""
             |ORDER BY type(edge), objectLabel.pageRank desc, objectLabel.id
             |WITH type(edge) as relation, collect(distinct objectLabel)[0 .. ${limit}] as distinctObjectLabelsByRelation
             |UNWIND distinctObjectLabelsByRelation as objectLabel
             |MATCH ${filters.subjectLabel.map(_ => s"(:${LabelLabel} {id: $$subjectLabel})<-[:${LabelRelationshipType}]-").getOrElse("")}
             |(subject:${NodeLabel})-[edge]->(object:${NodeLabel})-[:${LabelRelationshipType}]->(objectLabel)
             |WHERE type(edge) = relation
             |WITH edge, subject, object, objectLabel
             |ORDER BY type(edge), objectLabel.pageRank desc, objectLabel.id, object.id
             |""".stripMargin
        case KgTopEdgesSortField.ObjectPageRank =>
          s"""
             |ORDER BY object.pageRank DESC
             |WITH type(edge) as relation, collect([edge, subject, object])[0 .. ${limit}] as groupByRelation
             |UNWIND groupByRelation as group
             |WITH group[0] as edge, group[1] as subject, group[2] as object
             |""".stripMargin
      }

      transaction.run(
        s"""
           |${edgeCypher}
           |${cypher}
           |RETURN type(edge), subject.id, object.id, ${edgePropertyNamesString}
           |""".stripMargin,
        toTransactionRunParameters(caseClassToMap(filters))
      ).toEdges
    }


    final override def getTotalEdgesCount: Int =
      transaction.run(
        s"""
           |MATCH (subject:${NodeLabel})-[r]->(object:${NodeLabel})
           |WHERE NOT type(r) = "${PathRelationshipType}"
           |RETURN COUNT(r) as count
           |""".stripMargin
      ).single().get("count").asInt()

    final override def getTotalNodesCount: Int =
      transaction.run(s"MATCH (n:${NodeLabel}) RETURN COUNT(n) as count").single().get("count").asInt()

    final override def isEmpty: Boolean =
      transaction.run("MATCH (n) RETURN COUNT(n) as count").single().get("count").asInt() == 0

    final override def search(limit: Int, offset: Int, query: KgSearchQuery, sorts: Option[List[KgSearchSort]]): List[KgSearchResult] = {
      val cypher = KgNodeQueryFulltextCypher(query)

      transaction.run(
        s"""${cypher}
           |RETURN labels(node)[0] as type, node.label, ${nodePropertyNamesString}
           |ORDER BY ${(List("score desc") ++ sorts.getOrElse(List()).map(sort => s"node.${if (sort.field == KgSearchSortField.PageRank) "pageRank" else sort.field.value.toLowerCase()} ${if (sort.direction == SortDirection.Ascending) "asc" else "desc"}")).mkString(", ")}
           |SKIP ${offset}
           |LIMIT ${limit}
           |""".stripMargin,
        toTransactionRunParameters(cypher.bindings)
      ).asScala.toList.map { record =>
        record.get("type").asString match {
          case NodeLabel => KgNodeSearchResult(record.toNode)
          case LabelLabel => KgNodeLabelSearchResult(
            nodeLabel = record.get("node.label").asString,
            sourceIds = record.get("node.sources").asString.split(ListDelimChar).toList
          )
          case SourceLabel => KgSourceSearchResult(
            sourceId = record.get("node.id").asString
          )
        }
      }
    }

    final override def searchCount(query: KgSearchQuery): Int = {
      val cypher = KgNodeQueryFulltextCypher(query)

      transaction.run(
        s"""${cypher}
           |RETURN COUNT(node)
           |""".stripMargin,
        toTransactionRunParameters(cypher.bindings)
      ).single().get("COUNT(node)").asInt
    }

    final override def searchFacets(query: KgSearchQuery): KgSearchFacets = {
      val cypher = KgNodeQueryFulltextCypher(query)

      // Get sources
      val sourceIds =
        transaction.run(
          s"""
             |${cypher}
             |MATCH (node)-[:${SourceRelationshipType}]->(source:${SourceLabel})
             |RETURN DISTINCT source.id
             |""".stripMargin,
          toTransactionRunParameters(cypher.bindings)
        ).asScala.map(record => record.get("source.id").asString()).toSet.toList

      KgSearchFacets(
        sourceIds = sourceIds.map(sourceId => StringFacetValue(count = 1, value = sourceId))
      )
    }

    // https://stackoverflow.com/questions/1226555/case-class-to-map-in-scala
    // filters out None values and calls get on Some values
    private def caseClassToMap(cc: Product) = cc.getClass.getDeclaredFields.map( _.getName ).zip( cc.productIterator.to ).filter((mapping) => !mapping._2.isInstanceOf[Option[Any]] || mapping._2.asInstanceOf[Option[Any]].isDefined).map(mapping => if (mapping._2.isInstanceOf[Option[Any]]) (mapping._1, mapping._2.asInstanceOf[Option[Any]].get) else mapping).toMap

    private def filterEdgesCypher(filters: KgEdgeFilters): String = {
      var matchCypher: String = ""

      if (filters.objectId.isDefined) {
        matchCypher = s"MATCH (subject:${NodeLabel})-[edge]->(object:${NodeLabel} {id: $$objectId})"
      } else if (filters.objectLabel.isDefined) {
        matchCypher = s"MATCH (subject:${NodeLabel})-[edge]->(object:${NodeLabel})-[:${LabelRelationshipType}]->(:${LabelLabel} {id: $$objectLabel}) " +
          s"MATCH (object)-[:${LabelRelationshipType}]->(objectLabel:${LabelLabel})"
      } else if (filters.subjectId.isDefined) {
        matchCypher = s"MATCH (subject:${NodeLabel} {id: $$subjectId})-[edge]->(object:${NodeLabel})"
      } else if (filters.subjectLabel.isDefined) {
        matchCypher = s"MATCH (subjectLabel:${LabelLabel} {id: $$subjectLabel})<-[:${LabelRelationshipType}]-(subject:${NodeLabel})-[edge]->(object:${NodeLabel})-[:${LabelRelationshipType}]->(objectLabel:${LabelLabel})"
      } else {
        throw new UnsupportedOperationException
      }

      s"""
         |${matchCypher}
         |WHERE type(edge)<>"${PathRelationshipType}"
         |WITH edge, subject, object${if (filters.objectLabel.isDefined || filters.subjectLabel.isDefined) ", objectLabel" else ""}
         |""".stripMargin
    }

    private final case class KgNodeQueryFulltextCypher(
                                                bindings: Map[String, Any],
                                                fulltextCall: String
                                              ) {
      final override def toString = fulltextCall
    }

    private object KgNodeQueryFulltextCypher {
      def apply(query: KgSearchQuery): KgNodeQueryFulltextCypher = {

        val luceneSearchTerms =
          List("id:*") ++
          query.text.map(text => List(s"(${text})")).getOrElse(List()) ++ {
          if (query.filters.isDefined && query.filters.get.sourceIds.isDefined) {
            query.filters.get.sourceIds.get.exclude.toList.flatMap(
              _.map(excludeSourceId => s"NOT sources:${excludeSourceId}"
              )) ++
              query.filters.get.sourceIds.get.include.toList.flatMap(
                _.map(includeSourceId => s"sources:${includeSourceId}"
                ))
          } else {
            List()
          }
        }

        KgNodeQueryFulltextCypher(
          bindings = List("text" ->  luceneSearchTerms.mkString(" AND ") ).toMap,
          fulltextCall = """CALL db.index.fulltext.queryNodes("node", $text) YIELD node, score""",
        )
      }
    }

    private def toTransactionRunParameters(map: Map[String, Any]) =
      map.asJava.asInstanceOf[java.util.Map[String, Object]]


  }

  final override def getEdges(filters: KgEdgeFilters, limit: Int, offset: Int, sort: KgEdgesSort): List[KgEdge] =
    withReadTransaction {
      _.getEdges(filters, limit, offset, sort)
    }

  final override def getNodeById(id: String): Option[KgNode] =
    withReadTransaction {
      _.getNodeById(id)
    }

  final override def getNodesByLabel(label: String): List[KgNode] =
    withReadTransaction {
      _.getNodesByLabel(label)
    }

  //  override def getPaths: List[KgPath] =
  //    withSession { session =>
  //      session.readTransaction { transaction =>
  //        transaction.run(
  //          s"""MATCH (subjectNode:Node)-[path:PATH]->(objectNode:Node)
  //            |RETURN objectNode.id, subjectNode.id, ${pathPropertyNamesString}
  //            |""".stripMargin
  //        ).toPaths
  //      }
  //    }
  //
  override def getPathById(id: String): Option[KgPath] =
    withReadTransaction {
      _.getPathById(id)
    }

  final override def getRandomNode: KgNode =
    withReadTransaction {
      _.getRandomNode
    }

  final override def getSourcesById: Map[String, KgSource] =
    withReadTransaction {
      _.getSourcesById
    }

  override def getTopEdges(filters: KgEdgeFilters, limit: Int, sort: KgTopEdgesSort): List[KgEdge] =
    withReadTransaction {
      _.getTopEdges(filters, limit, sort)
    }

  final override def getTotalEdgesCount: Int =
    withReadTransaction {
      _.getTotalEdgesCount
    }

  final override def getTotalNodesCount: Int =
    withReadTransaction {
      _.getTotalNodesCount
    }

  final override def isEmpty: Boolean =
    withReadTransaction {
      _.isEmpty
    }

  final override def search(limit: Int, offset: Int, query: KgSearchQuery, sorts: Option[List[KgSearchSort]]): List[KgSearchResult] =
    withReadTransaction {
      _.search(limit, offset, query, sorts)
    }

  final override def searchCount(query: KgSearchQuery): Int =
    withReadTransaction {
      _.searchCount(query)
    }

  final override def searchFacets(query: KgSearchQuery): KgSearchFacets =
    withReadTransaction {
      _.searchFacets(query)
    }
}
