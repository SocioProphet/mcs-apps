package formats.cskg

import com.github.tototoshi.csv._
import models.cskg
import models.cskg.Node

final class CskgNodesCsvReader extends CskgCsvReader[Node] {
  def read(csvReader: CSVReader): Stream[Node] =
    csvReader.toStreamWithHeaders.map(row =>
      cskg.Node(
        aliases = row.getNonBlank("aliases").map(aliases => aliases.split(' ').toList),
        datasource = row("datasource"),
        id = row("id"),
        label = row("label"),
        other = row.getNonBlank("other"),
        pos = row.getNonBlank("pos")
      )
    )
}