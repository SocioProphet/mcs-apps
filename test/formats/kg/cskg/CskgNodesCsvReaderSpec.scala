package formats.kg.cskg

import io.github.tetherlessworld.twxplore.lib.base.WithResource
import org.scalatest.{Matchers, WordSpec}
import stores.kg.{TestKgData, TestKgDataResources}

class CskgNodesCsvReaderSpec extends WordSpec with Matchers with WithResource {
  "CSKG nodes CSV reader" can {
    "read the test data" in {
      withResource (CskgNodesCsvReader.open(TestKgDataResources.getNodesCsvResourceAsStream())) { reader =>
        val nodes = reader.iterator.toList
        nodes.size should be > 0
        for (node <- nodes) {
          node.id should not be empty
          node.label should not be empty
          node.datasource should not be empty
        }
      }
    }
  }
}
