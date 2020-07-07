import {BrowserRouter, Route, Switch, Redirect} from "react-router-dom";
import {KgNodeSearchResultsPage} from "kg/components/kg/search/KgNodeSearchResultsPage";
import * as React from "react";
import {KgNodePage} from "kg/components/kg/node/KgNodePage";
import {NoRoute} from "benchmark/components/error/NoRoute";
import {KgHomePage} from "kg/components/kg/KgHomePage";
import {Hrefs} from "kg/Hrefs";
import {RandomKgNodePage} from "kg/components/kg/node/RandomKgNodePage";
import {kgId} from "shared/api/kgId";

export const Routes: React.FunctionComponent = () => (
  <BrowserRouter>
    <Switch>
      <Route exact path={Hrefs.home}>
        <Redirect to={Hrefs.kgs} />
      </Route>

      <Route
        exact
        path={Hrefs.kg({id: kgId}).nodeSearch()}
        component={KgNodeSearchResultsPage}
      />
      <Route
        path={Hrefs.kg({id: kgId}).node({id: ":nodeId", idEncoded: true})}
        component={KgNodePage}
      />
      <Route
        exact
        path={Hrefs.kg({id: kgId}).randomNode}
        component={RandomKgNodePage}
      />
      <Route exact path={Hrefs.kgs} component={KgHomePage} />

      <Route component={NoRoute} />
    </Switch>
  </BrowserRouter>
);
