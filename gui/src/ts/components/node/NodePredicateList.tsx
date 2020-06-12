import {NodePageQuery_nodeById_subjectOfEdges} from "api/queries/types/NodePageQuery";
import {
  Card,
  CardContent,
  List,
  ListItem,
  createStyles,
  makeStyles,
} from "@material-ui/core";
import * as React from "react";
import {NodeLink} from "./NodeLink";

const useStyles = makeStyles(() =>
  createStyles({
    edgeListRoot: {
      margin: "20px",
    },
    edgeListContent: {
      display: "flex",
      flexDirection: "row",
      padding: "0",
      "&:last-child": {
        paddingBottom: "0",
      },
      "& > *": {
        padding: "16px",
      },
      "&:last-child > *": {
        paddingBottom: "24px",
      },
    },
    edgeListTitle: {
      flex: "0 0 16em",
      background: "#F4F4F4",
    },
  })
);

const PredicateEdgeList: React.FunctionComponent<{
  edges: NodePageQuery_nodeById_subjectOfEdges[];
  predicate: string;
  datasource: string;
}> = ({edges, predicate, datasource}) => {
  const classes = useStyles();
  return (
    <Card className={classes.edgeListRoot} data-cy={`list-${predicate}-edges`}>
      <CardContent className={classes.edgeListContent}>
        <div className={classes.edgeListTitle} data-cy="edge-list-title">
          <p>{predicate}</p>
        </div>
        <List>
          {edges.map((edge) => (
            <ListItem data-cy="edge" key={edge.object}>
              <NodeLink node={edge.objectNode!} datasource={datasource} />
            </ListItem>
          ))}
        </List>
      </CardContent>
    </Card>
  );
};

export const NodePredicateList: React.FunctionComponent<{
  predicateSubjects: {
    [predicate: string]: NodePageQuery_nodeById_subjectOfEdges[];
  };
  datasource: string;
}> = ({predicateSubjects, datasource}) => {
  return (
    <React.Fragment>
      {Object.keys(predicateSubjects).map((predicate) => (
        <PredicateEdgeList
          edges={predicateSubjects[predicate]!}
          predicate={predicate}
          key={predicate}
          datasource={datasource}
        />
      ))}
    </React.Fragment>
  );
};