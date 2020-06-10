/* tslint:disable */
/* eslint-disable */
// This file was automatically generated and should not be edited.

import { NodeFilters } from "./../../graphqlGlobalTypes";

// ====================================================
// GraphQL query operation: NodeSearchResultsPageQuery
// ====================================================

export interface NodeSearchResultsPageQuery_matchingNodes {
  __typename: "Node";
  aliases: string[] | null;
  datasource: string;
  id: string;
  label: string | null;
  other: string | null;
  pos: string | null;
}

export interface NodeSearchResultsPageQuery {
  matchingNodes: NodeSearchResultsPageQuery_matchingNodes[];
  matchingNodesCount: number;
}

export interface NodeSearchResultsPageQueryVariables {
  filters: NodeFilters;
  limit: number;
  offset: number;
  text: string;
  withCount: boolean;
}
