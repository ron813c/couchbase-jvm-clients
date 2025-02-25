package com.couchbase.client.scala

import com.couchbase.client.core.error.DocumentNotFoundException
import com.couchbase.client.scala.env.ClusterEnvironment
import com.couchbase.client.scala.util.ScalaIntegrationTest
import com.couchbase.client.test.{ClusterAwareIntegrationTest, ClusterType, IgnoreWhen}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}

import scala.util.{Failure, Success}

@TestInstance(Lifecycle.PER_CLASS)
class BinarySpec extends ScalaIntegrationTest {

  private var cluster: Cluster       = _
  private var coll: BinaryCollection = _

  @BeforeAll
  def beforeAll(): Unit = {
    cluster = connectToCluster()
    val bucket = cluster.bucket(config.bucketname)
    coll = bucket.defaultCollection.binary
  }

  @AfterAll
  def afterAll(): Unit = {
    cluster.disconnect()
  }

  @Test
  def blocking_increment() {
    val docId = TestUtils.docId()
    coll.increment(docId, 3, Option(0)) match {
      case Success(result) => assert(result.content == 0) // initial value returned
      case Failure(err)    => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def blocking_increment_exists() {
    val docId = TestUtils.docId()
    coll.increment(docId, 0, Option(0))
    coll.increment(docId, 5, Option(999)) match {
      case Success(result) => assert(result.content == 5) // new value value returned
      case Failure(err)    => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def blocking_increment_exists_no_initial() {
    val docId = TestUtils.docId()
    coll.increment(docId, 0, Option(0))
    coll.increment(docId, 5) match {
      case Success(result) => assert(result.content == 5) // new value value returned
      case Failure(err)    => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def blocking_increment_no_initial() {
    val docId = TestUtils.docId()
    coll.increment(docId, 3) match {
      case Success(result)                         => assert(false, s"success not expected")
      case Failure(err: DocumentNotFoundException) =>
      case Failure(err)                            => assert(false, s"unexpected error $err")
    }
  }
  @Test
  def blocking_decrement() {
    val docId = TestUtils.docId()
    coll.decrement(docId, 3, Option(0)) match {
      case Success(result) => assert(result.content == 0) // initial value returned
      case Failure(err)    => assert(false, s"unexpected error $err")
    }
  }

  @Test
  @IgnoreWhen(clusterTypes = Array(ClusterType.MOCKED))
  def blocking_decrement_exists_at_0() {
    val docId = TestUtils.docId()
    coll.decrement(docId, 0, Option(0))
    coll.decrement(docId, 5, Option(999)) match {
      case Success(result) =>
        assert(result.content == 0) // remember decrement won't go below 0
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def blocking_decrement_exists_at_100() {
    val docId = TestUtils.docId()
    coll.decrement(docId, 0, Option(100))
    coll.decrement(docId, 5, Option(999)) match {
      case Success(result) => assert(result.content == 95)
      case Failure(err)    => assert(false, s"unexpected error $err")
    }
  }

  @Test
  @IgnoreWhen(clusterTypes = Array(ClusterType.MOCKED))
  def blocking_decrement_exists_no_initial() {
    val docId = TestUtils.docId()
    coll.decrement(docId, 0, Option(0))
    coll.decrement(docId, 5) match {
      case Success(result) =>
        assert(result.content == 0) // remember decrement won't go below 0
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def blocking_decrement_no_initial() {
    val docId = TestUtils.docId()
    coll.decrement(docId, 3) match {
      case Success(result)                         => assert(false, s"success not expected")
      case Failure(err: DocumentNotFoundException) =>
      case Failure(err)                            => assert(false, s"unexpected error $err")
    }
  }

}
