package formats.kg.kgtk

import formats.CsvReader
import com.github.tototoshi.csv.{CSVReader, TSVFormat}
import models.kg.KgEdge
import models.kg.KgNode
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.io.{FileNotFoundException, InputStream}

import scala.util.Try

final class KgtkTsvReader(csvReader: CSVReader) extends CsvReader[Tuple3[KgEdge, KgNode, KgNode]](csvReader) {
  private final val ListDelim = "|";

  private val logger = LoggerFactory.getLogger(getClass)

  def iterator: Iterator[Tuple3[KgEdge, KgNode, KgNode]] =
    csvReader.iteratorWithHeaders.map(row => {
      val node1Labels = row.getList("node1;label", ListDelim)
      val node2Labels = row.getList("node2;label", ListDelim)
      (
        KgEdge(
          datasource = row("source"),
          datasources = row.getList("source", "|"),
          id = row("id"),
          `object` = row("node2"),
          other = None,
          predicate = row("relation"),
          subject = row("node1"),
          weight = Try(row.getNonBlank("weight").get.toDouble).toOption
        ),
        KgNode(
          aliases = Some(node1Labels.slice(1, node1Labels.size)),
          datasource = row("source"),
          datasources = row.getList("source", ListDelim),
          id = row("node1"),
          label = node1Labels(0),
          other = None,
          pos = None
        ),
        KgNode(
          aliases = Some(node2Labels.slice(1, node2Labels.size)),
          datasource = row("source"),
          datasources = row.getList("source", ListDelim),
          id = row("node2"),
          label = node2Labels(0),
          other = None,
          pos = None
        )
      )
    }
    )
}

object KgtkTsvReader {
  private val csvFormat = new TSVFormat {}
  def open(filePath: Path) = new KgtkTsvReader(CsvReader.openCsvReader(filePath, csvFormat))
  def open(inputStream: InputStream) =
    Option(inputStream) getOrElse (throw new FileNotFoundException("KgtkTsvReader missing resource"))
}