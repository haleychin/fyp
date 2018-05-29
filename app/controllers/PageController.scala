package controllers

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable.Map
import play.api.mvc._
import play.api.libs.ws._
import play.api.libs.json.Json
import play.api.Logger
import play.api.Configuration
import reactivemongo.bson.BSONObjectID
import actions.{AuthenticatedAction, AuthenticatedRequest}
import models._

class PageController @Inject()(
  cc: ControllerComponents,
  authenticatedAction: AuthenticatedAction,
  ws: WSClient,
  config: Configuration,
  userRepo: UserRepository,
  subjectRepo: SubjectRepository,
  classRepo: ClassRepository
)(
  implicit ec: ExecutionContext
) extends AbstractController(cc) {

  case class JsonSubject(code: String, classes: List[JsonClass])
  case class JsonClass(category: String, group: Int, students: List[String])
  implicit val classReader = Json.reads[JsonClass]
  implicit val subjectReader = Json.reads[JsonSubject]

  def index = authenticatedAction.async { implicit request =>
    getClasses(request.email).flatMap { maybeClasses =>
      maybeClasses match {
        case Some(classes) =>
          Future(Ok(views.html.index(classes)))
        case None =>
          fetchClasses(request.email).map { maybeResult =>
            maybeResult match {
              case Some((semester, subjects)) =>
                saveClasses(request.email, semester, subjects)
                Ok(views.html.index(Map()))
              case None =>
                Ok(views.html.index(Map()))
            }
          }
      }
    }
  }

  def login = Action { implicit request_ =>
    try {
      request_.session("accessToken")
      request_.session("name")
      request_.session("email")
      Redirect(routes.PageController.index())
    } catch {
      case e: NoSuchElementException =>
        // Implicit session for login template
        implicit val session = new AuthenticatedRequest[AnyContent](
          name = "",
          email = "",
          request = request_
        )
        Ok(views.html.login())
    }
  }

  // Get saved classes from database
  def getClasses(email: String): Future[Option[Map[Subject, List[Class]]]] = {
    (for {
      // Get user id
      userId <- userRepo.findUserByEmail(email).map { user =>
        user.get._id.get
      }
      // List subjects under user
      subjects <- subjectRepo.findSubjectsByUserId(userId).map { subjects =>
        if (subjects.isEmpty) {
          throw new Exception("no subjects found")
        }
        subjects
      }
      // Map subject to classes
      subjectMap <- {
        var subjectMap = Map[Subject, List[Class]]()
        Future.traverse(subjects) { subject =>
          classRepo.findClassesBySubjectId(subject._id.get).map { classes =>
            subjectMap += (subject -> classes)
          }
        }.map { _ =>
          subjectMap
        }
      }
    } yield Some(subjectMap)) fallbackTo Future(None)
  }

  // Fetch latest active classes from API
  def fetchClasses(email: String):
    Future[Option[(String, List[JsonSubject])]] = {
    // GET request to fetch active subjects
    ws.url(config.get[String]("my.api.icheckin.classUrl"))
      .addQueryStringParameters("email" -> email)
      .get().map { response =>
        val semester = (response.json \ "semester").as[String]
        val subjects = (response.json \ "subjects").as[List[JsonSubject]]
        semester match {
          case "" =>
            // Email not found
            None
          case _ =>
            Some((semester, subjects))
        }
      }
  }

  // Save classes to database
  def saveClasses(
    email: String,
    activeSemester: String,
    activeSubjects: List[JsonSubject]
  ): Future[Unit] = {
    for {
      // Create subjects under user
      subjectIdMap <- userRepo.findUserByEmail(email).flatMap { user =>
        var subjectIdMap = Map[BSONObjectID, List[JsonClass]]()
        Future.traverse(activeSubjects) { subject =>
          val subjectId = BSONObjectID.generate
          subjectRepo.create(Subject(
            _id = Option(subjectId),
            code = subject.code,
            semester = activeSemester,
            userId = user.get._id.get
          )).map { _ =>
            Logger.info(
              s"Created Subject(${subject.code}, ${activeSemester}, " +
              s"${subjectId})"
            )
            subjectIdMap += (subjectId -> subject.classes)
          }
        }.map { _ =>
          println(subjectIdMap)
          subjectIdMap
        }
      }
      // Create classes under subject
      _ <- Future {
        subjectIdMap.foreach { case(subjectId_, classes) =>
          classes.foreach { class_ =>
            classRepo.create(Class(
              category = class_.category,
              group = class_.group,
              students = class_.students,
              subjectId = subjectId_
            )).map { _ =>
              Logger.info(
                s"Created Class(${subjectId_}, ${class_.category}, " +
                s"${class_.group})"
              )
            }
          }
        }
      }
    } yield Unit
  }

}
