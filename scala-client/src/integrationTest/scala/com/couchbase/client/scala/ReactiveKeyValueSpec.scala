package com.couchbase.client.scala

import com.couchbase.client.core.error.{
  DocumentLockedException,
  DocumentNotFoundException,
  TimeoutException
}
import com.couchbase.client.scala.util.ScalaIntegrationTest
import com.couchbase.client.test.{ClusterType, IgnoreWhen}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api._
import reactor.core.scala.publisher.SMono

import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
@TestInstance(Lifecycle.PER_CLASS)
class ReactiveKeyValueSpec extends ScalaIntegrationTest {

  private var cluster: Cluster         = _
  private var blocking: Collection     = _
  private var coll: ReactiveCollection = _

  @BeforeAll
  def beforeAll(): Unit = {
    cluster = connectToCluster()
    val bucket = cluster.bucket(config.bucketname)
    blocking = bucket.defaultCollection
    coll = blocking.reactive

  }

  @AfterAll
  def afterAll(): Unit = {
    cluster.disconnect()
  }

  def wrap[T](in: SMono[T]): Try[T] = {
    try {
      Success(in.block())
    } catch {
      case NonFatal(err) => Failure(err)
    }
  }

  @Test
  def insert() {
    val docId = TestUtils.docId()
    coll.remove(docId)
    val content = ujson.Obj("hello" -> "world")
    assert(wrap(coll.insert(docId, content)).isSuccess)

    wrap(coll.get(docId)) match {
      case Success(result) =>
        result.contentAs[ujson.Obj] match {
          case Success(body) =>
            assert(body("hello").str == "world")
          case Failure(err) => assert(false, s"unexpected error $err")
        }
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }

  @Test
  @IgnoreWhen(clusterTypes = Array(ClusterType.MOCKED))
  def exists() {
    val docId = TestUtils.docId()
    coll.remove(docId)

    wrap(coll.exists(docId)) match {
      case Success(result) => assert(!result.exists)
      case Failure(err)    => assert(false, s"unexpected error $err")
    }

    assert(wrap(coll.insert(docId, ujson.Obj())).isSuccess)

    wrap(coll.exists(docId)) match {
      case Success(result) => assert(result.exists)
      case Failure(err)    => assert(false, s"unexpected error $err")
    }
  }

  private def cleanupDoc(docIdx: Int = 0): String = {
    val docId = TestUtils.docId(docIdx)
    docId
  }

  @Test
  def insert_returns_cas() {
    val docId = cleanupDoc()

    val content = ujson.Obj("hello" -> "world")
    wrap(coll.insert(docId, content)) match {
      case Success(result) => assert(result.cas != 0)
      case Failure(err)    => assert(false, s"unexpected error $err")
    }
  }
  @Test
  def insert_without_expiry() {
    val docId = cleanupDoc()

    val content = ujson.Obj("hello" -> "world")
    assert(wrap(coll.insert(docId, content, expiry = 5.seconds)).isSuccess)

    wrap(coll.get(docId)) match {
      case Success(result) => assert(result.expiry.isEmpty)
      case Failure(err)    => assert(false, s"unexpected error $err")
      case _               => assert(false, s"unexpected error")
    }
  }

  @IgnoreWhen(clusterTypes = Array(ClusterType.MOCKED))
  @Test
  def insert_with_expiry() {
    val docId = cleanupDoc()

    val content = ujson.Obj("hello" -> "world")
    assert(wrap(coll.insert(docId, content, expiry = 5.seconds)).isSuccess)

    wrap(coll.get(docId, withExpiry = true)) match {
      case Success(result) => assert(result.expiry.isDefined)
      case Failure(err)    => assert(false, s"unexpected error $err")
      case _               => assert(false, s"unexpected error")
    }
  }

  @IgnoreWhen(clusterTypes = Array(ClusterType.MOCKED))
  @Test
  def get_and_lock() {
    val docId = TestUtils.docId()
    coll.remove(docId)
    val content      = ujson.Obj("hello" -> "world")
    val insertResult = wrap(coll.insert(docId, content)).get

    wrap(coll.getAndLock(docId, 30.seconds)) match {
      case Success(result) =>
        assert(result.cas != 0)
        assert(result.cas != insertResult.cas)
        assert(result.contentAs[ujson.Obj].get == content)
      case Failure(err) => assert(false, s"unexpected error $err")
      case _            => assert(false, s"unexpected error")
    }

    wrap(coll.getAndLock(docId, 30.seconds, timeout = 100.milliseconds)) match {
      case Success(result)                => assert(false, "should not have been able to relock locked doc")
      case Failure(err: TimeoutException) =>
      case Failure(err)                   => assert(false, s"unexpected error $err")
      case _                              => assert(false, s"unexpected error")
    }
  }

  @IgnoreWhen(clusterTypes = Array(ClusterType.MOCKED))
  @Test
  def get_and_touch() {
    val docId = TestUtils.docId()
    coll.remove(docId)
    val content      = ujson.Obj("hello" -> "world")
    val insertResult = wrap(coll.insert(docId, content, expiry = 10.seconds)).get

    assert(insertResult.cas != 0)

    wrap(coll.getAndTouch(docId, 1.second)) match {
      case Success(result) =>
        assert(result.cas != 0)
        assert(result.cas != insertResult.cas)
        assert(result.contentAs[ujson.Obj].get == content)
      case Failure(err) => assert(false, s"unexpected error $err")
      case _            => assert(false, s"unexpected error")
    }
  }

  @Test
  def remove() {
    val docId = TestUtils.docId()
    coll.remove(docId)
    val content = ujson.Obj("hello" -> "world")
    assert(wrap(coll.insert(docId, content)).isSuccess)

    assert(wrap(coll.remove(docId)).isSuccess)

    wrap(coll.get(docId)) match {
      case Success(result)                         => assert(false, s"doc $docId exists and should not")
      case Failure(err: DocumentNotFoundException) =>
      case Failure(err)                            => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def upsert_when_doc_does_not_exist() {
    val docId = TestUtils.docId()
    coll.remove(docId)
    val content      = ujson.Obj("hello" -> "world")
    val upsertResult = wrap(coll.upsert(docId, content))

    upsertResult match {
      case Success(result) =>
        assert(result.cas != 0)
        assert(result.mutationToken.isDefined)
      case Failure(err) => assert(false, s"unexpected error $err")
    }

    wrap(coll.get(docId)) match {
      case Success(result) =>
        assert(result.cas == upsertResult.get.cas)
        assert(result.contentAs[ujson.Obj].get == content)
      case Failure(err) => assert(false, s"unexpected error $err")
      case _            => assert(false, s"unexpected error")
    }
  }

  @Test
  def upsert_when_doc_does_exist() {
    val docId = TestUtils.docId()
    coll.remove(docId)
    val content      = ujson.Obj("hello" -> "world")
    val insertResult = wrap(coll.insert(docId, content))

    assert(insertResult.isSuccess)

    val content2     = ujson.Obj("hello" -> "world2")
    val upsertResult = wrap(coll.upsert(docId, content2))

    upsertResult match {
      case Success(result) =>
        assert(result.cas != 0)
        assert(result.cas != insertResult.get.cas)
        assert(result.mutationToken.isDefined)
      case Failure(err) => assert(false, s"unexpected error $err")
    }

    wrap(coll.get(docId)) match {
      case Success(result) =>
        assert(result.cas == upsertResult.get.cas)
        assert(result.contentAs[ujson.Obj].get == content2)
      case Failure(err) => assert(false, s"unexpected error $err")
      case _            => assert(false, s"unexpected error")
    }
  }
  @Test
  def replace_when_doc_does_not_exist() {
    val docId = TestUtils.docId()
    coll.remove(docId)
    val content      = ujson.Obj("hello" -> "world")
    val upsertResult = wrap(coll.replace(docId, content))

    upsertResult match {
      case Success(result)                         => assert(false, s"doc should not exist")
      case Failure(err: DocumentNotFoundException) =>
      case Failure(err)                            => assert(false, s"unexpected error $err")
    }
  }
  @Test
  def replace_when_doc_does_exist_with() {
    val docId = TestUtils.docId()
    coll.remove(docId)
    val content      = ujson.Obj("hello" -> "world")
    val insertResult = wrap(coll.insert(docId, content))

    assert(insertResult.isSuccess)

    val content2      = ujson.Obj("hello" -> "world2")
    val replaceResult = wrap(coll.replace(docId, content2))

    replaceResult match {
      case Success(result) =>
        assert(result.cas != 0)
        assert(result.cas != insertResult.get.cas)
        assert(result.mutationToken.isDefined)
      case Failure(err) => assert(false, s"unexpected error $err")
    }

    wrap(coll.get(docId)) match {
      case Success(result) =>
        assert(result.cas == replaceResult.get.cas)
        assert(result.contentAs[ujson.Obj].get == content2)
      case Failure(err) => assert(false, s"unexpected error $err")
      case _            => assert(false, s"unexpected error")
    }
  }
  @Test
  def replace_when_doc_does_exist_with_2() {
    val docId = TestUtils.docId()
    coll.remove(docId)
    val content      = ujson.Obj("hello" -> "world")
    val insertResult = wrap(coll.insert(docId, content))

    assert(insertResult.isSuccess)

    val content2      = ujson.Obj("hello" -> "world2")
    val replaceResult = wrap(coll.replace(docId, content2, insertResult.get.cas))

    replaceResult match {
      case Success(result) =>
        assert(result.cas != 0)
        assert(result.cas != insertResult.get.cas)
        assert(result.mutationToken.isDefined)
      case Failure(err) => assert(false, s"unexpected error $err")
    }

    wrap(coll.get(docId)) match {
      case Success(result) =>
        assert(result.cas == replaceResult.get.cas)
        assert(result.contentAs[ujson.Obj].get == content2)
      case Failure(err) => assert(false, s"unexpected error $err")
      case _            => assert(false, s"unexpected error")
    }
  }
}
