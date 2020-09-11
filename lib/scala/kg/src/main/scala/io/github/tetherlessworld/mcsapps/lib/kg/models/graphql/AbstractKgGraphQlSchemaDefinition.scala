package io.github.tetherlessworld.mcsapps.lib.kg.models.graphql

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.github.tetherlessworld.mcsapps.lib.kg.models.kg.{KgEdge, KgNode, KgPath, KgSource}
import io.github.tetherlessworld.mcsapps.lib.kg.stores._
import io.github.tetherlessworld.twxplore.lib.base.models.graphql.BaseGraphQlSchemaDefinition
import sangria.macros.derive.{AddFields, deriveEnumType, deriveInputObjectType, deriveObjectType}
import sangria.marshalling.circe._
import sangria.schema.{Argument, Field, FloatType, IntType, ListInputType, ListType, ObjectType, OptionInputType, OptionType, StringType, UnionType, fields}

abstract class AbstractKgGraphQlSchemaDefinition extends BaseGraphQlSchemaDefinition {
  // Scalar arguments
  val IdArgument = Argument("id", StringType)
  val LabelArgument = Argument("label", StringType)

  // Object types
  // KgSource
  implicit val KgSourceType = deriveObjectType[KgGraphQlSchemaContext, KgSource]()

  private def mapSources(sourceIds: List[String], sourcesById: Map[String, KgSource]): List[KgSource] =
    sourceIds.map(sourceId => sourcesById.getOrElse(sourceId, KgSource(sourceId)))

  // Can't use deriveObjectType for KgEdge and KgNode because we need to define them recursively
  // https://github.com/sangria-graphql/sangria/issues/54

  // KgEdge
  implicit lazy val KgEdgeType: ObjectType[KgGraphQlSchemaContext, KgEdge] = ObjectType("KgEdge", () => fields[KgGraphQlSchemaContext, KgEdge](
    Field("id", StringType, resolve = _.value.id),
    Field("label", OptionType(StringType), resolve = ctx => ctx.value.labels.headOption),
    Field("object", StringType, resolve = _.value.`object`),
    Field("objectNode", OptionType(KgNodeType), resolve = ctx => ctx.ctx.kgQueryStore.getNodeById(ctx.value.`object`)),
    Field("predicate", StringType, resolve = _.value.predicate),
    Field("sourceIds", ListType(StringType), resolve = _.value.sourceIds),
    Field("sources", ListType(KgSourceType), resolve = ctx => mapSources(ctx.value.sourceIds, ctx.ctx.kgQueryStore.getSourcesById)),
    Field("subject", StringType, resolve = _.value.subject),
    Field("subjectNode", OptionType(KgNodeType), resolve = ctx => ctx.ctx.kgQueryStore.getNodeById(ctx.value.subject)),
  ))
  // KgNode
  implicit lazy val KgNodeType: ObjectType[KgGraphQlSchemaContext, KgNode] = ObjectType("KgNode", () => fields[KgGraphQlSchemaContext, KgNode](
    Field("aliases", OptionType(ListType(StringType)), resolve = ctx => if (ctx.value.labels.size > 1) Some(ctx.value.labels.slice(1, ctx.value.labels.size)) else None),
    Field("id", StringType, resolve = _.value.id),
    Field("label", OptionType(StringType), resolve = ctx => ctx.value.labels.headOption),
    Field("pageRank", FloatType, resolve = _.value.pageRank.get),
    Field("pos", OptionType(StringType), resolve = _.value.pos),
    Field("sourceIds", ListType(StringType), resolve = _.value.sourceIds),
    Field("sources", ListType(KgSourceType), resolve = ctx => mapSources(ctx.value.sourceIds, ctx.ctx.kgQueryStore.getSourcesById)),
    Field("topSubjectOfEdges", ListType(KgEdgeType), arguments = LimitArgument :: Nil, resolve = ctx => ctx.ctx.kgQueryStore.getTopEdges(filters = KgEdgeFilters(subjectId = Some(ctx.value.id)), limit = ctx.args.arg(LimitArgument), sort = KgTopEdgesSort(KgTopEdgesSortField.ObjectPageRank, SortDirection.Descending)))
  ))
  val KgNodesByLabelType = deriveObjectType[KgGraphQlSchemaContext, AbstractKgGraphQlSchemaDefinition.KgNodesByLabel](
    AddFields(
      Field("sourceIds", ListType(StringType), resolve = ctx => ctx.value.nodes.flatMap(_.sourceIds)),
      Field("topSubjectOfEdges", ListType(KgEdgeType), arguments = LimitArgument :: Nil, resolve = ctx => ctx.ctx.kgQueryStore.getTopEdges(filters = KgEdgeFilters(subjectLabel = Some(ctx.value.nodeLabel)), limit = ctx.args.arg(LimitArgument), sort = KgTopEdgesSort(KgTopEdgesSortField.ObjectLabelPageRank, SortDirection.Descending)))
    )
  )

  // KgPath
  val KgPathType = deriveObjectType[KgGraphQlSchemaContext, KgPath](
    AddFields(
      Field("edges", ListType(KgEdgeType), resolve = _.value.edges)
    )
  )
  // Search
  implicit val StringFacetType = deriveObjectType[KgGraphQlSchemaContext, StringFacetValue]()
  val KgSearchFacetsType = deriveObjectType[KgGraphQlSchemaContext, KgSearchFacets]()
  implicit val KgEdgeSearchResultType = deriveObjectType[KgGraphQlSchemaContext, KgEdgeSearchResult]()
  implicit val KgEdgeLabelSearchResultType = deriveObjectType[KgGraphQlSchemaContext, KgEdgeLabelSearchResult]()
  implicit val KgNodeLabelSearchResultType = deriveObjectType[KgGraphQlSchemaContext, KgNodeLabelSearchResult]()
  implicit val KgNodeSearchResultType = deriveObjectType[KgGraphQlSchemaContext, KgNodeSearchResult]()
  implicit val KgSourceSearchResultType = deriveObjectType[KgGraphQlSchemaContext, KgSourceSearchResult]()
  val KgSearchResultType = UnionType("KgSearchResult", types = List(KgEdgeSearchResultType, KgEdgeLabelSearchResultType, KgNodeLabelSearchResultType, KgNodeSearchResultType, KgSourceSearchResultType))

  // Input enum types
  implicit val KgNodeSortableFieldType = KgSearchSortField.sangriaType
  implicit val SortDirectionType = SortDirection.sangriaType

  // Input object decoders
  implicit val stringFilterDecoder: Decoder[StringFacetFilter] = deriveDecoder
  implicit val kgSearchFiltersDecoder: Decoder[KgSearchFilters] = deriveDecoder
  implicit val kgSearchQueryDecoder: Decoder[KgSearchQuery] = deriveDecoder
  implicit val kgSearchSortDecoder: Decoder[KgSearchSort] = deriveDecoder
  // Input object types
  implicit val StringFacetFilterType = deriveInputObjectType[StringFacetFilter]()
  implicit val KgSearchFiltersType = deriveInputObjectType[KgSearchFilters]()
  implicit val KgSearchQueryType = deriveInputObjectType[KgSearchQuery]()
  implicit val KgSearchSortType = deriveInputObjectType[KgSearchSort]()

  // Object argument types types
  val KgSearchQueryArgument = Argument("query", KgSearchQueryType)
  val KgSearchSortsArgument = Argument("sorts", OptionInputType(ListInputType(KgSearchSortType)))

  // Query types
  val KgQueryType = ObjectType("Kg", fields[KgGraphQlSchemaContext, String](
    Field("search", ListType(KgSearchResultType), arguments = LimitArgument :: OffsetArgument :: KgSearchQueryArgument :: KgSearchSortsArgument :: Nil, resolve = ctx => ctx.ctx.kgQueryStore.search(limit = ctx.args.arg(LimitArgument), offset = ctx.args.arg(OffsetArgument), query = ctx.args.arg(KgSearchQueryArgument), sorts = ctx.args.arg(KgSearchSortsArgument).map(_.toList))),
    Field("searchCount", IntType, arguments = KgSearchQueryArgument :: Nil, resolve = ctx => ctx.ctx.kgQueryStore.searchCount(query = ctx.args.arg(KgSearchQueryArgument))),
    Field("searchFacets", KgSearchFacetsType, arguments = KgSearchQueryArgument :: Nil, resolve = ctx => ctx.ctx.kgQueryStore.searchFacets(query = ctx.args.arg(KgSearchQueryArgument))),
    Field("nodeById", OptionType(KgNodeType), arguments = IdArgument :: Nil, resolve = ctx => ctx.ctx.kgQueryStore.getNodeById(ctx.args.arg(IdArgument))),
    Field("nodesByLabel", KgNodesByLabelType, arguments = LabelArgument :: Nil, resolve = ctx => {
      val label = ctx.args.arg(LabelArgument)
      AbstractKgGraphQlSchemaDefinition.KgNodesByLabel(nodeLabel = label, nodes = ctx.ctx.kgQueryStore.getNodesByLabel(label))
    }),
    Field("pathById", OptionType(KgPathType), arguments = IdArgument :: Nil, resolve = ctx => ctx.ctx.kgQueryStore.getPathById(ctx.args.arg(IdArgument))),
    Field("randomNode", KgNodeType, resolve = ctx => ctx.ctx.kgQueryStore.getRandomNode),
    Field("sources", ListType(KgSourceType), resolve = ctx => ctx.ctx.kgQueryStore.getSources),
    Field("totalEdgesCount", IntType, resolve = ctx => ctx.ctx.kgQueryStore.getTotalEdgesCount),
    Field("totalNodesCount", IntType, resolve = ctx => ctx.ctx.kgQueryStore.getTotalNodesCount)
  ))
}

object AbstractKgGraphQlSchemaDefinition {
  final case class KgNodesByLabel(nodeLabel: String, nodes: List[KgNode])
}
