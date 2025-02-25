package com.couchbase.client.scala.encodings

import com.couchbase.client.core.error.DecodingFailedException
import com.couchbase.client.scala.codec.JsonSerializer
import com.couchbase.client.scala.codec.{
  DocumentFlags,
  RawBinaryTranscoder,
  RawJsonTranscoder,
  RawStringTranscoder
}
import com.couchbase.client.scala.util.ScalaIntegrationTest
import com.couchbase.client.scala.{Cluster, Collection, TestUtils}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api.{AfterAll, BeforeAll, Test, TestInstance}

import scala.util.{Failure, Success}

@TestInstance(Lifecycle.PER_CLASS)
class EncodingsSpec extends ScalaIntegrationTest {

  private var cluster: Cluster = _
  private var coll: Collection = _

  @BeforeAll
  def beforeAll(): Unit = {
    cluster = connectToCluster()
    val bucket = cluster.bucket(config.bucketname)
    coll = bucket.defaultCollection
  }

  @AfterAll
  def afterAll(): Unit = {
    cluster.disconnect()
  }

  def getContent(docId: String): ujson.Obj = {
    coll.get(docId) match {
      case Success(result) =>
        result.contentAs[ujson.Obj] match {
          case Success(content) =>
            content
          case Failure(err) =>
            assert(false, s"unexpected error $err")
            null
        }
      case Failure(err) =>
        assert(false, s"unexpected error $err")
        null
    }
  }

  @Test
  def encode_encoded_json_string() {
    val content = """{"hello":"world"}"""
    val docId   = TestUtils.docId()
    coll.insert(docId, content).get

    val doc = coll.get(docId).get

    assert(doc.flags == DocumentFlags.Json)
  }

  @Test
  def encode_encoded_json_string_directly_as_string() {
    val content = """{"hello":"world"}"""
    val docId   = TestUtils.docId()
    coll.insert(docId, content, transcoder = RawStringTranscoder.Instance).get

    val doc = coll.get(docId).get

    assert(doc.flags == DocumentFlags.String)
  }

  @Test
  def decode_encoded_json_string_as_json() {
    val content = """{"hello":"world"}"""
    val docId   = TestUtils.docId()
    coll.insert(docId, content).get

    coll.get(docId).get.contentAs[ujson.Obj] match {
      case Success(out)                          => assert(false)
      case Failure(err: DecodingFailedException) => // ujson.Str cannot be cast to ujson.Obj
      case Failure(err)                          => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def decode_encoded_json_string() {
    val content = """{"hello":"world"}"""
    val docId   = TestUtils.docId()
    coll.insert(docId, content, transcoder = RawJsonTranscoder.Instance).get

    coll.get(docId).get.contentAs[ujson.Obj] match {
      case Success(out) =>
        assert(out("hello").str == "world")
      case Failure(err) => assert(false, s"unexpected error $err")
    }

    coll.get(docId).get.contentAs[String] match {
      case Success(out)                          => assert(false)
      case Failure(err: DecodingFailedException) =>
      case Failure(err)                          => assert(false, s"unexpected error $err")
    }

    coll.get(docId, transcoder = RawJsonTranscoder.Instance).get.contentAs[String] match {
      case Success(out) => assert(out == content)
      case Failure(err) => assert(false, s"unexpected error $err")
    }

    coll.get(docId, transcoder = RawStringTranscoder.Instance).get.contentAs[String] match {
      case Success(out) => assert(out == content)
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }
  @Test
  def encode_raw_string() {
    val content = """hello, world!"""
    val docId   = TestUtils.docId()
    coll.insert(docId, content).get

    val doc = coll.get(docId).get

    // The content is legal Json, but app probably meant to use RawStringTranscoder
    assert(doc.flags == DocumentFlags.Json)
  }
  @Test
  def encode_raw_string_directly_as_string() {
    val content = """hello, world!"""
    val docId   = TestUtils.docId()
    coll.insert(docId, content, transcoder = RawStringTranscoder.Instance).get

    val doc = coll.get(docId).get

    assert(doc.flags == DocumentFlags.String)
  }
  @Test
  def decode_raw_string_as_json_should_fail() {
    val content = """hello, world!"""
    val docId   = TestUtils.docId()
    coll.insert(docId, content).get

    coll.get(docId).get.contentAs[ujson.Obj] match {
      case Success(out)                          => assert(false, "should not succeed")
      case Failure(err: DecodingFailedException) =>
      case Failure(err)                          => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def decode_raw_string() {
    val content = """hello, world!"""
    val docId   = TestUtils.docId()
    coll.insert(docId, content, transcoder = RawStringTranscoder.Instance).get

    coll.get(docId).get.contentAs[String] match {
      case Success(out)                          => assert(false)
      case Failure(err: DecodingFailedException) =>
      case Failure(err)                          => assert(false, s"unexpected error $err")
    }

    coll.get(docId, transcoder = RawStringTranscoder.Instance).get.contentAs[String] match {
      case Success(out) => assert(out == content)
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }
  @Test
  def encode_json_bytes() {
    val content              = ujson.Obj("hello" -> "world")
    val encoded: Array[Byte] = ujson.transform(content, ujson.BytesRenderer()).toBytes
    val docId                = TestUtils.docId()
    coll.insert(docId, encoded, transcoder = RawJsonTranscoder.Instance).get

    val doc = coll.get(docId).get

    assert(doc.flags == DocumentFlags.Json)
  }

  @Test
  def decode_json_bytes_as_json() {
    val content              = ujson.Obj("hello" -> "world")
    val encoded: Array[Byte] = ujson.transform(content, ujson.BytesRenderer()).toBytes
    val docId                = TestUtils.docId()
    coll.insert(docId, encoded, transcoder = RawJsonTranscoder.Instance).get

    coll.get(docId).get.contentAs[ujson.Obj] match {
      case Success(out) =>
        assert(out("hello").str == "world")
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def decode_json_bytes_as_string() {
    val content              = ujson.Obj("hello" -> "world")
    val encoded: Array[Byte] = ujson.transform(content, ujson.BytesRenderer()).toBytes
    val docId                = TestUtils.docId()
    coll.insert(docId, encoded, transcoder = RawJsonTranscoder.Instance).get

    coll.get(docId).get.contentAs[String] match {
      case Success(out)                          => assert(false)
      case Failure(err: DecodingFailedException) =>
      case Failure(err)                          => assert(false, s"unexpected error $err")
    }

    coll.get(docId, transcoder = RawJsonTranscoder.Instance).get.contentAs[String] match {
      case Success(out) => assert(out == content.toString)
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def decode_json_bytes_as_string_with_transcoder() {
    val content              = ujson.Obj("hello" -> "world")
    val encoded: Array[Byte] = ujson.transform(content, ujson.BytesRenderer()).toBytes
    val docId                = TestUtils.docId()
    coll.insert(docId, encoded, transcoder = RawJsonTranscoder.Instance).get

    coll.get(docId, transcoder = RawStringTranscoder.Instance).get.contentAs[String] match {
      case Success(out) => assert(out == """{"hello":"world"}""")
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def decode_json_bytes_written_directly_as_json_into() {
    val content              = ujson.Obj("hello" -> "world")
    val encoded: Array[Byte] = ujson.transform(content, ujson.BytesRenderer()).toBytes
    val docId                = TestUtils.docId()
    coll.insert(docId, encoded, transcoder = RawJsonTranscoder.Instance).get

    coll.get(docId).get.contentAs[ujson.Obj] match {
      case Success(out) =>
        assert(out("hello").str == "world")
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def decode_json_bytes_written_directly_as_binary_into() {
    val content              = ujson.Obj("hello" -> "world")
    val encoded: Array[Byte] = ujson.transform(content, ujson.BytesRenderer()).toBytes
    val docId                = TestUtils.docId()
    coll.insert(docId, encoded, transcoder = RawBinaryTranscoder.Instance).get

    coll.get(docId).get.contentAs[ujson.Obj] match {
      case Success(out) =>
      // stored as binary but it's still legit json, seems ok to be able to decode as json
      case Failure(err) => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def encode_raw_bytes() {
    val content = Array[Byte](1, 2, 3, 4)
    val docId   = TestUtils.docId()
    coll.insert(docId, content, transcoder = RawBinaryTranscoder.Instance).get

    val doc = coll.get(docId).get

    assert(doc.flags == DocumentFlags.Binary)
  }

  @Test
  def raw_json_bytes_as_json_should_fail() {
    val content = Array[Byte](1, 2, 3, 4)
    val docId   = TestUtils.docId()
    coll.insert(docId, content, transcoder = RawJsonTranscoder.Instance).get

    coll.get(docId).get.contentAs[ujson.Obj] match {
      case Success(out)                          => assert(false, "should not succeed")
      case Failure(err: DecodingFailedException) =>
      case Failure(err)                          => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def decode_raw_bytes_written_directly_as_binary_as_string() {
    val content = Array[Byte](1, 2, 3, 4)
    val docId   = TestUtils.docId()
    coll.insert(docId, content, transcoder = RawBinaryTranscoder.Instance).get

    coll.get(docId).get.contentAs[ujson.Obj] match {
      case Success(out)                          => assert(false, "should not succeed")
      case Failure(err: DecodingFailedException) =>
      case Failure(err)                          => assert(false, s"unexpected error $err")
    }
  }

  @Test
  def decode_raw_bytes_as_bytes() {
    val content = Array[Byte](1, 2, 3, 4)
    val docId   = TestUtils.docId()
    coll.insert(docId, content, transcoder = RawJsonTranscoder.Instance).get

    assert(
      coll
        .get(docId, transcoder = RawJsonTranscoder.Instance)
        .get
        .contentAs[Array[Byte]]
        .get sameElements content
    )
  }

  @Test
  def decode_raw_bytes_written_directly_as_binary_as() {
    val content = Array[Byte](1, 2, 3, 4)
    val docId   = TestUtils.docId()
    coll.insert(docId, content, transcoder = RawBinaryTranscoder.Instance).get

    assert(
      coll
        .get(docId, transcoder = RawBinaryTranscoder.Instance)
        .get
        .contentAs[Array[Byte]]
        .get sameElements content
    )
  }
}
