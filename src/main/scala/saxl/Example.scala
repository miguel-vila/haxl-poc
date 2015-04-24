package saxl

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scalaz.syntax.applicative._
import scalaz.std.stream.streamInstance

/**
 * Created by mglvl on 23/04/15.
 */

/**
 * Tipos bases
 */
trait ExampleReturn
trait ExampleRequest[+T]

class Example extends FetchInstance[ExampleReturn,ExampleRequest]  {

  /**
   * Tipos que representan las posibles respuestas de un servicio
   */
  case class PostId(id: Int) extends ExampleReturn
  case class PostViews(views: Int) extends ExampleReturn
  type PostDate = Date

  trait PostContent extends ExampleReturn
  object PostContent extends PostContent

  case class PostInfo(
                       postId: PostId,
                       postDate: PostDate,
                       postTopic: String) extends ExampleReturn

  case class PostIds(postIds: Stream[PostId]) extends ExampleReturn

  /**
   * Tipos que representan los requests que atienden los servicios
   */
  case object GetPostIds extends ExampleRequest[PostIds]

  case class GetPostInfo(postId: PostId) extends ExampleRequest[PostInfo]

  case class GetPostContent(postId: PostId) extends ExampleRequest[PostContent]

  case class GetPostViews(postId: PostId) extends ExampleRequest[PostViews]

  /**
   * Valores/Funciones Fetch que representan los queries básicos
   * a partir de los cuales se realiza la composición para implementar
   * alguna lógica de negocio
   */
  val getPostIds = dataFetch(GetPostIds)

  def getPostInfo(postId: PostId) = dataFetch(GetPostInfo(postId))

  def getPostContent(postId: PostId) = dataFetch(GetPostContent(postId))

  def getPostViews(postId: PostId) = dataFetch(GetPostViews(postId))

  /**
   * Representa un documento HTML
   */
  trait HTML extends ExampleReturn
  object HTML extends HTML

  /**
   * Funciones de "Renderización"
   */
  def renderPosts(content: Stream[(PostInfo,PostContent)]): HTML = HTML
  def renderPostList(content: Stream[(PostInfo,PostContent)]): HTML = HTML
  def renderTopics(topicsCount: Map[String, Int]): HTML = HTML
  def renderLeftPane(sidePane: HTML, topicsPane: HTML): HTML = HTML
  def renderPage(leftPane: HTML, mainPane: HTML): HTML = HTML

  /**
   * Composición de servicios / Lógica de negocio
   */
  val getAllPostsInfo: Fetch[Stream[PostInfo]] = getPostIds.flatMap { case PostIds(postIds) =>
    Fetch.traverse(postIds)(getPostInfo)
  }

  val mainPane: Fetch[HTML] = {
    for {
      posts <- getAllPostsInfo
      ordered = posts.sortBy(_.postDate).take(5)
      content <- Fetch.traverse(ordered)(p => getPostContent(p.postId))
      renderingContent = ordered zip content
    } yield renderPosts(renderingContent)
  }

  def getPostDetails(postId: PostId): Fetch[(PostInfo, PostContent)] = {
    ( getPostInfo(postId) |@| getPostContent(postId) ) { (_,_) }
  }

  val popularPosts: Fetch[HTML] = for {
    postIds <- getPostIds
    pids = postIds.postIds
    views <- Fetch.traverse(pids)(getPostViews)
    ordered = (pids zip views).sortBy(_._2.views).map(_._1).take(5)
    content <- Fetch.traverse(ordered)(getPostDetails)
  } yield renderPostList(content)

  val topics: Fetch[HTML] = for {
    posts <- getAllPostsInfo
    topicCounts = posts.groupBy(_.postTopic).mapValues(_.size)
  } yield renderTopics(topicCounts)

  val leftPane: Fetch[HTML] = (popularPosts |@| topics)(renderLeftPane)

  val pageHTML: Fetch[HTML] = (leftPane |@| mainPane)(renderPage)

  /**
   * Implementaciones de los servicios base
   */
  def getPostIdsImpl(): Future[PostIds] = {
    Future.successful(PostIds(Stream(PostId(1),PostId(2),PostId(3),PostId(4),PostId(5),PostId(6),PostId(7))))
  }

  val postInfoData = Map(
    PostId(1) -> PostInfo(PostId(1),new Date(),"topic post 1"),
    PostId(2) -> PostInfo(PostId(2),new Date(),"topic post 2"),
    PostId(3) -> PostInfo(PostId(3),new Date(),"topic post 3"),
    PostId(4) -> PostInfo(PostId(4),new Date(),"topic post 4"),
    PostId(5) -> PostInfo(PostId(5),new Date(),"topic post 5"),
    PostId(6) -> PostInfo(PostId(6),new Date(),"topic post 6"),
    PostId(7) -> PostInfo(PostId(7),new Date(),"topic post 7")
  )

  def getPostInfoImpl(postId: PostId): Future[PostInfo] = {
    Future.successful(postInfoData(postId))
  }

  def getPostContentImpl(postId: PostId): Future[PostContent] = {
    Future.successful(PostContent)
  }

  val postViewsData = Map(
    PostId(1) -> 1,
    PostId(2) -> 2,
    PostId(3) -> 3,
    PostId(4) -> 4,
    PostId(5) -> 5,
    PostId(6) -> 6,
    PostId(7) -> 7
  )

  def getPostViewsImpl(postId: PostId): Future[PostViews] = {
    Future.successful(PostViews(postViewsData(postId)))
  }

  /**
   * Ejecución
   */

  import scala.concurrent.ExecutionContext.Implicits.global


  /**
   * Función que ejecuta un query y realiza el side effect correspondiente
   */
  def processExampleRequest(br: BlockedRequest[ExampleReturn]): Future[Unit] = {
    br.request match {
      case GetPostIds             => processBlockedRequest( br, getPostIdsImpl() )
      case GetPostInfo(postId)    => processBlockedRequest( br, getPostInfoImpl(postId) )
      case GetPostContent(postId) => processBlockedRequest( br, getPostContentImpl(postId) )
      case GetPostViews(postId)   => processBlockedRequest( br, getPostViewsImpl(postId) )
    }
  }

  def fetcher(br: Seq[BlockedRequest[ExampleReturn]]): Future[Unit] = {
    for {
      _ <- Future.traverse(br)(processExampleRequest)
    } yield ()
  }

}

/*
object Test extends Example with App {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val cache = Atom(DataCache())
  println("About to run")
  runFetch(pageHTML, fetcher).onComplete {
    case Success(html) => println(s"Success!: $html")
    case Failure(t) => println(s"Failure: $t")
  }

}
*/