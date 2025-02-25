package com.couchbase.client.scala.manager

import com.couchbase.client.core.error.CouchbaseException
import com.couchbase.client.scala.manager.user.{UserNotFoundException, _}
import com.couchbase.client.scala.util.CouchbasePickler._
import com.couchbase.client.scala.util.ScalaIntegrationTest
import com.couchbase.client.scala.{Cluster, Collection}
import com.couchbase.client.test._
import com.couchbase.mock.deps.org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import com.couchbase.mock.deps.org.apache.http.client.CredentialsProvider
import com.couchbase.mock.deps.org.apache.http.client.methods.{CloseableHttpResponse, HttpGet}
import com.couchbase.mock.deps.org.apache.http.impl.client.{
  BasicCredentialsProvider,
  CloseableHttpClient,
  HttpClientBuilder
}
import com.couchbase.mock.deps.org.apache.http.util.EntityUtils
import org.junit.jupiter.api.Assertions.{assertEquals, assertThrows}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api._
import reactor.core.scala.publisher.SMono

@TestInstance(Lifecycle.PER_CLASS)
@IgnoreWhen(clusterTypes = Array(ClusterType.MOCKED))
class UserManagerSpec extends ScalaIntegrationTest {
  private var cluster: Cluster           = _
  private var users: ReactiveUserManager = _
  private var coll: Collection           = _

  private val Username                 = "integration-test-user"
  private val Admin                    = Role("admin")
  private val ReadOnlyAdmin            = Role("ro_admin")
  private val BucketFullAccessWildcard = Role("bucket_full_access", Some("*"))

  @BeforeAll
  def setup(): Unit = {
    cluster = connectToCluster()
    val bucket = cluster.bucket(config.bucketname)
    coll = bucket.defaultCollection
    users = cluster.reactive.users
  }

  @AfterAll
  def tearDown(): Unit = {
    cluster.disconnect()
  }

  @Test
  def access(): Unit = {
    val users: UserManager                 = cluster.users
    val usersAsync: AsyncUserManager       = cluster.async.users
    val usersReactive: ReactiveUserManager = cluster.reactive.users
  }

  def checkRoleOrigins(userMeta: UserAndMetadata, expected: String*): Unit = {
    val expectedRolesAndOrigins            = expected.toSet
    val actualRolesAndOrigins: Set[String] = userMeta.effectiveRolesAndOrigins.map(_.toString).toSet
    assert(expectedRolesAndOrigins == actualRolesAndOrigins)
  }

  private def dropUserQuietly(name: String): Unit = {
    users
      .dropUser(name)
      .onErrorResume(err => {
        if (err.isInstanceOf[UserNotFoundException]) SMono.empty
        else SMono.raiseError(err)
      })
      .block()
  }

  private def assertUserAbsent(username: String): Unit = {
    val allUsers = users.getAllUsers().map(_.user).collectSeq().block()
    assert(!allUsers.exists(_.username == username))
    assertThrows(
      classOf[UserNotFoundException],
      () => users.getUser(username, AuthDomain.Local).block()
    )
  }
  @AfterEach
  @BeforeEach
  def dropTestUser(): Unit = {
    dropUserQuietly(Username)
    assertUserAbsent(Username)
  }

  @Test
  def parseUserAndMetadataUpickle(): Unit = {
    val raw =
      """{"id":"integration-test-user","domain":"local","roles":[{"role":"admin",
        |"origins":[{"type":"user"}]}],"groups":[],"external_groups":[],"name":"Integration Test User",
        |"password_change_date":"2019-08-15T17:36:03.000Z"}""".stripMargin
    val user = read[UserAndMetadata](raw)
    assert(user.domain == AuthDomain.Local)
    assert(user.innateRoles.size == 1)
    assert(user.groups.size == 0)
    assert(user.username == "integration-test-user")

  }

  @Test
  def parseRole(): Unit = {
    val role = read[RoleAndOrigins](
      """{"role":"bucket_full_access","bucket_name":"*","origins":[{"type":"user"}]}"""
    )
    assert(role.role.bucket.contains("*"))

  }

  @Test
  def parseUserAndMetadata2(): Unit = {
    val raw =
      """{"id":"integration-test-user","domain":"local","roles":[{"role":"bucket_full_access",
        |"bucket_name":"*","origins":[{"type":"user"}]},{"role":"ro_admin","origins":[{"type":"user"}]}],"groups":[],
        |"external_groups":[],"name":"Renamed","password_change_date":"2019-08-16T16:16:28.000Z"}""".stripMargin
    val user = read[UserAndMetadata](raw)
    assert(user.innateRoles.size == 2)
    assert(user.innateRoles.head.bucket.contains("*"))
  }

  @Test
  def parseUserAndMetadata3(): Unit = {
    val raw =
      """{"id":"integration-test-user","domain":"local","roles":[{"role":"ro_admin","origins":[{"type":"group","name":"group-a"}]}],"groups":["group-a"],"external_groups":[],"name":"","password_change_date":"2019-08-16T17:26:31.000Z"}"""
    val user = read[UserAndMetadata](raw)
    assert(user.username == "integration-test-user")
  }

  @Test
  def parseRoleAndOriginsUpickle(): Unit = {
    val raw  = """[{"role":"admin","origins":[{"type":"user"}]}]"""
    val role = read[Seq[RoleAndOrigins]](raw)
    assert(role(0).role.name == "admin")
  }

  @Test
  def parseRoleAndOriginUpickle(): Unit = {
    val raw  = """{"role":"admin","origins":[{"type":"user"}]}"""
    val role = read[RoleAndOrigins](raw)
    assert(role.role.name == "admin")
  }

  @Test
  def availableRoles(): Unit = {
    val roles: Seq[RoleAndDescription] = users.availableRoles().collectSeq.block()
    // Full results vary by server version, but should at least contain the admin role.
    assert(roles.map(_.role).contains(Admin))
  }

  @Test
  def getAll(): Unit = {
    val user = User(Username, "Integration Test User", Seq(), Seq(Admin), Some("password"))
    users.upsertUser(user).block()
    val allUsers: Seq[UserAndMetadata] = users.getAllUsers().collectSeq().block()
    val justUsers                      = allUsers.map(_.user)
    assert(justUsers.exists(_.username == user.username))
  }

  @Test
  def createWithBadRole(): Unit = {
    assertThrows(
      classOf[CouchbaseException],
      () => {
        val user: User = User(Username)
          .password("password")
          .roles(Role("bogus"))
          .displayName("Integration Test User")
        users.upsertUser(user).block()
      }
    )
  }

  @Test
  def create(): Unit = {
    val origPassword: String = "password"
    val newPassword: String  = "newpassword"

    users
      .upsertUser(
        User(Username)
          .password(origPassword)
          .displayName("Integration Test User")
          .roles(Admin)
      )
      .block()

    // must be a specific kind of admin for this to succeed (not exactly sure which)
    assertCanAuthenticate(Username, origPassword)

    var userMeta: UserAndMetadata = users.getUser(Username, AuthDomain.Local).block()
    assertEquals(AuthDomain.Local, userMeta.domain)
    assertEquals("Integration Test User", userMeta.user.displayName)
    val expectedRoles = Seq(Admin)
    assertEquals(expectedRoles, userMeta.innateRoles)
    assertEquals(expectedRoles, userMeta.user.roles)
    assertEquals(expectedRoles, userMeta.effectiveRoles)

    checkRoleOrigins(userMeta, "admin<-[user]")

    users.upsertUser(User(Username).displayName("Renamed").roles(Admin))

    assertCanAuthenticate(Username, origPassword)

    users
      .upsertUser(
        User(Username)
          .displayName("Renamed")
          .roles(ReadOnlyAdmin, BucketFullAccessWildcard)
          .password(newPassword)
      )
      .block()

    assertCanAuthenticate(Username, newPassword)

    userMeta = users.getUser(Username, AuthDomain.Local).block()
    assertEquals("Renamed", userMeta.user.displayName)
    assertEquals(Set(ReadOnlyAdmin, BucketFullAccessWildcard), userMeta.innateRoles.toSet)

    checkRoleOrigins(userMeta, "ro_admin<-[user]", "bucket_full_access[*]<-[user]")
  }

  private def assertCanAuthenticate(username: String, password: String): Unit = {
    val provider: CredentialsProvider = new BasicCredentialsProvider
    provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password))
    var client: CloseableHttpClient     = null
    var response: CloseableHttpResponse = null
    try {
      client = HttpClientBuilder.create.setDefaultCredentialsProvider(provider).build
      val node: TestNodeConfig = ClusterAwareIntegrationTest.config.nodes.get(0)
      val hostAndPort: String  = node.hostname + ":" + node.ports.get(Services.MANAGER)
      response = client.execute(new HttpGet("http://" + hostAndPort + "/pools"))
      val body: String = EntityUtils.toString(response.getEntity)
      assertEquals(200, response.getStatusLine.getStatusCode, "Server response: " + body)
    } finally {
      if (client != null) client.close()
      if (response != null) response.close()
    }
  }

  @Test
  def dropAbsentUser(): Unit = {
    val name: String = "doesnotexist"
    val e: UserNotFoundException =
      assertThrows(classOf[UserNotFoundException], () => users.dropUser(name).block())
    assertEquals(name, e.username)
    assertEquals(AuthDomain.Local, e.domain)
  }
}
