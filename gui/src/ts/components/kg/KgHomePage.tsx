import * as React from "react";

import {KgNodeSearchBox} from "components/kg/search/KgNodeSearchBox";
import {Frame} from "components/frame/Frame";

import {
  Grid,
  Container,
  Typography,
  makeStyles,
  createStyles,
  Button,
} from "@material-ui/core";

import {useHistory, Link} from "react-router-dom";

import {Hrefs} from "Hrefs";
import {KgNodeSearchBoxValue} from "models/kg/KgNodeSearchBoxValue";
import {kgId} from "api/kgId";
import {useQuery} from "@apollo/react-hooks";
import {KgDataSummaryQuery} from "api/queries/kg/types/KgDataSummaryQuery";
import * as KgDataSummaryQueryDocument from "api/queries/kg/KgDataSummaryQuery.graphql";

const useStyles = makeStyles((theme) =>
  createStyles({
    container: {
      paddingTop: theme.spacing(5),
    },
    title: {
      fontFamily: "Hiragino Maru Gothic Pro",
    },
    primaryText: {
      color: theme.palette.primary.main,
    },
  })
);

export const KgHomePage: React.FunctionComponent = () => {
  const classes = useStyles();

  const history = useHistory();

  const query = useQuery<KgDataSummaryQuery>(KgDataSummaryQueryDocument, {
    variables: {kgId},
  });

  const [search, setSearch] = React.useState<KgNodeSearchBoxValue>(null);

  const onSearchChange = (newValue: KgNodeSearchBoxValue) =>
    setSearch(newValue);

  const onSearchSubmit = () => {
    if (search === null) {
      return;
    }

    switch (search.__typename) {
      case "KgNode":
        history.push(Hrefs.kg({id: kgId}).node({id: search.id}));
        break;
      case "KgNodeSearchVariables":
        history.push(Hrefs.kg({id: kgId}).nodeSearch(search));
        break;
      default:
        const _exhaustiveCheck: never = search;
        _exhaustiveCheck;
    }
  };

  return (
    <Frame {...query}>
      {({data}) => (
        <Container maxWidth="md" className={classes.container}>
          <Grid container direction="column" spacing={3}>
            <Grid item>
              <Typography variant="h2" className={classes.title}>
                MCS Portal
              </Typography>
            </Grid>
            <Grid item>
              {data && (
                <React.Fragment>
                  <Typography>
                    Search{" "}
                    <strong data-cy="totalNodeCount">
                      {data.kgById.totalNodesCount} nodes
                    </strong>{" "}
                    with{" "}
                    <strong data-cy="totalEdgeCount">
                      {data.kgById.totalEdgesCount} relationships
                    </strong>
                  </Typography>

                  <KgNodeSearchBox
                    autoFocus
                    datasources={data.kgById.datasources}
                    placeholder="Search a word or try a query"
                    showIcon={true}
                    onChange={onSearchChange}
                  />
                  <br />
                  <Button
                    color="primary"
                    variant="contained"
                    onClick={onSearchSubmit}
                  >
                    Search
                  </Button>
                  <Button
                    color="primary"
                    component={Link}
                    to={Hrefs.kg({id: kgId}).randomNode}
                  >
                    Show me something interesting
                  </Button>
                </React.Fragment>
              )}
            </Grid>
          </Grid>
        </Container>
      )}
    </Frame>
  );
};
