package formats.cskg

import org.scalatest.{Matchers, WordSpec}
import stores.TestData

class CskgEdgesCsvReaderSpec extends WordSpec with Matchers {
  "CSKG edges CSV reader" can {
    val sut = new CskgEdgesCsvReader()

    "read the test data" in {
      val inputStream = TestData.getEdgesCsvResourceAsStream()
      try {
        val edges = sut.read(inputStream).toList
        edges.size should be > 0
        for (edge <- edges) {
          edge.subject should not be empty
          edge.`object` should not be empty
          edge.datasource should not be empty
        }
      } finally {
        inputStream.close()
      }
    }
  }
}
