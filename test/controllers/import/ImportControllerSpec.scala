package controllers.`import`

import java.io.{File, FileOutputStream}
import java.nio.file.{Files, Path}

import controllers.import_.ImportController
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.PlaySpec
import play.api.mvc.{AnyContent, Results}
import play.api.test.{FakeRequest, Helpers}
import play.api.test._
import play.api.test.Helpers._
import stores.{MemStore, Store, TestData, TestStore}

import scala.reflect.io.Directory

class ImportControllerSpec extends PlaySpec with BeforeAndAfterEach with Results {
  private var sut: ImportController = _
  private var importDirectoryPath: Path = _
  private var store: Store = _

  override protected def afterEach(): Unit = {
    new Directory(importDirectoryPath.toFile).deleteRecursively()
  }

  override protected def beforeEach(): Unit = {
    importDirectoryPath = Files.createTempDirectory(null)
    store = new MemStore
    sut = new ImportController(importDirectoryPath, store)
    sut.setControllerComponents(Helpers.stubControllerComponents())
  }

  "The import controller" should {
    "clear the store" in {
      store.putNodes(TestData.nodes)
      store.isEmpty must be(false)
      val result = sut.clear()(FakeRequest())
//      val bodyText = contentAsString(result)
////      bodyText must be("ok")
      store.isEmpty must be(true)
    }

    "put edges to the store" in {
      store.getTotalEdgesCount must be (0)
      store.putNodes(TestData.nodes)
      val sourceFilePath = new File(getClass.getResource(TestData.EdgesCsvBz2ResourceName).toURI).toPath
      val destFilePath = importDirectoryPath.resolve(sourceFilePath.getFileName)
      Files.copy(sourceFilePath, destFilePath)
      val result = sut.putEdges(destFilePath.getFileName.toString)(FakeRequest())
      //      val bodyText = contentAsString(result)
      //      //      bodyText must be("ok")
      store.getTotalEdgesCount must be (TestData.edges.length)
    }

    "put paths to the store" in {
      store.getPaths.length must be (0)
      val sourceFilePath = new File(getClass.getResource(TestData.PathsJsonlResourceName).toURI).toPath
      val destFilePath = importDirectoryPath.resolve(sourceFilePath.getFileName)
      Files.copy(sourceFilePath, destFilePath)
      val result = sut.putPaths(destFilePath.getFileName.toString)(FakeRequest())
      //      val bodyText = contentAsString(result)
      //      //      bodyText must be("ok")
      store.getPaths.length must be (TestData.paths.length)
    }

    "put nodes to the store" in {
      store.getTotalNodesCount must be (0)
      val sourceFilePath = new File(getClass.getResource(TestData.NodesCsvBz2ResourceName).toURI).toPath
      val destFilePath = importDirectoryPath.resolve(sourceFilePath.getFileName)
      Files.copy(sourceFilePath, destFilePath)
      val result = sut.putNodes(destFilePath.getFileName.toString)(FakeRequest())
//      val bodyText = contentAsString(result)
//      //      bodyText must be("ok")
      store.getTotalNodesCount must be (TestData.nodes.length)
    }
  }

}