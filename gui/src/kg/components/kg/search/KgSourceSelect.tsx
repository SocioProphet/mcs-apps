import * as React from "react";

import {Select, MenuItem, Paper} from "@material-ui/core";
import {StringFacetFilter} from "shared/models/StringFacetFilter";
import {KgSource} from "shared/models/kg/source/KgSource";

export const KgSourceSelect: React.FunctionComponent<{
  sources: KgSource[];
  value?: StringFacetFilter;
  onChange?: (datasourceFilters: StringFacetFilter) => void;
  style?: React.CSSProperties;
}> = ({sources, value, onChange, style}) => {
  const [selectedSource, setSelectedSource] = React.useState<string>(
    value?.include?.[0] || ""
  );

  return (
    <Paper variant="outlined" square style={style} data-cy="sourceSelect">
      <Select
        displayEmpty
        value={selectedSource}
        onChange={(event: React.ChangeEvent<{value: unknown}>) => {
          const value = event.target.value as string;

          setSelectedSource(value);

          if (onChange) {
            onChange(value.length > 0 ? {include: [value]} : {});
          }
        }}
        renderValue={(selected) => (
          <span style={{marginLeft: "5px"}} data-cy="value">
            {(selected as string).length === 0 ? (
              <React.Fragment>All sources</React.Fragment>
            ) : (
              sources.find((source) => source.id === selected)!.label
            )}
          </span>
        )}
      >
        <MenuItem value="" data-cy="allSourcesSelectMenuItem">
          All Sources
        </MenuItem>
        {sources.map((source) => (
          <MenuItem
            key={source.id}
            value={source.id}
            data-cy="datasourceSelectMenuItem"
          >
            {source.label}
          </MenuItem>
        ))}
      </Select>
    </Paper>
  );
};
