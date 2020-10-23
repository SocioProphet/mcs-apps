import * as qs from "qs";
import {KgSearchVariables} from "shared/models/kg/search/KgSearchVariables";
import * as _ from "lodash";

declare var KG_BASE_HREF: string;

export class Hrefs {
  constructor(protected readonly baseHref: string) {}

  readonly gitHub = "https://github.com/tetherless-world/mcs-apps";

  kg(kwds: {id: string; idEncoded?: boolean}) {
    const kgPrefix = `${this.baseHref}kg/${
      kwds.idEncoded ? kwds.id : encodeURIComponent(kwds.id)
    }/`;
    return {
      get home() {
        return kgPrefix;
      },

      node(kwds: {id: string; idEncoded?: boolean}) {
        return (
          kgPrefix +
          `node/${kwds.idEncoded ? kwds.id : encodeURIComponent(kwds.id)}`
        );
      },

      nodeLabel(kwds: {label: string; labelEncoded?: boolean}) {
        return (
          kgPrefix +
          `nodeLabel/${
            kwds.labelEncoded ? kwds.label : encodeURIComponent(kwds.label)
          }`
        );
      },

      randomNode: kgPrefix + "randomNode",

      search(kwds?: KgSearchVariables) {
        const href = kgPrefix + "search";
        if (!kwds) {
          return href;
        }

        const {__typename, ...searchVariables} = kwds;

        const query = searchVariables.query;
        if (query && _.isEmpty(query.filters)) {
          delete query.filters;
        }

        return (
          href +
          qs.stringify(
            {
              limit: searchVariables.limit,
              offset: searchVariables.offset,
              query: !_.isEmpty(query) ? JSON.stringify(query) : undefined,
            },
            {addQueryPrefix: true}
          )
        );
      },

      source(kwds: {sourceId: string}) {
        return this.search({
          query: {
            filters: {
              sourceIds: {include: [kwds.sourceId]},
            },
          },
          __typename: "KgSearchVariables",
        });
      },
    };
  }
}
