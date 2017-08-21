package com.github.yoshiyoshifujii.aws.apigateway

import java.io.File

import com.amazonaws.services.apigateway.model._
import com.github.yoshiyoshifujii.cliformatter.CliFormatter

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import scala.util.Try

trait AWSApiGatewayRestApiWrapper extends AWSApiGatewayWrapper {

  def create(name: String, description: Option[String]) = Try {
    val request = new CreateRestApiRequest()
      .withName(name)
    description.foreach(request.setDescription)

    client.createRestApi(request)
  }

  def delete(restApiId: RestApiId) = Try {
    val request = new DeleteRestApiRequest()
      .withRestApiId(restApiId)

    client.deleteRestApi(request)
  }

  def get(restApiId: RestApiId) = Try {
    val request = new GetRestApiRequest()
      .withRestApiId(restApiId)

    toOpt(client.getRestApi(request))
  }

  def gets = Try {
    val request = new GetRestApisRequest()

    client.getRestApis(request)
  }

  def printGets = {
    for {
      l <- gets
    } yield {
      val p = CliFormatter(
        "Rest APIs",
        "Rest API Name" -> 30,
        "Created Date"  -> 30,
        "Rest API Id"   -> 15,
        "Description"   -> 30
      ).print4(l.getItems.sortBy(d => d.getName) map { d =>
        (d.getName, d.getCreatedDate.toString, d.getId, d.getDescription)
      }: _*)
      println(p)
    }
  }

  def `import`(body: File, failOnWarnings: Option[Boolean]) = Try {
    val request = new ImportRestApiRequest()
      .withBody(toByteBuffer(body))
    failOnWarnings.foreach(request.setFailOnWarnings(_))

    client.importRestApi(request)
  }

  def export(restApiId: RestApiId, stageName: StageName) = Try {
    val request = new GetExportRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)
      .withExportType("swagger")
      .addParametersEntry("extensions", "integrations")
      .withAccepts("application/json")

    client.getExport(request)
  }

  def put(restApiId: RestApiId, body: File, mode: PutMode, failOnWarnings: Option[Boolean]) = Try {
    val request = new PutRestApiRequest()
      .withRestApiId(restApiId)
      .withBody(toByteBuffer(body))
      .withMode(mode)
    failOnWarnings.foreach(request.setFailOnWarnings(_))

    client.putRestApi(request)
  }

  def createDeployment(restApiId: RestApiId,
                       stageName: StageName,
                       stageDescription: Option[StageDescription],
                       description: Option[String],
                       variables: Option[StageVariables]) = Try {
    val request = new CreateDeploymentRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)
    stageDescription.foreach(request.setStageDescription)
    description.foreach(request.setDescription)
    variables.foreach(v => request.setVariables(v.asJava))

    client.createDeployment(request)
  }

  def deleteDeployment(restApiId: RestApiId, deploymentId: DeploymentId) = Try {
    val request = new DeleteDeploymentRequest()
      .withRestApiId(restApiId)
      .withDeploymentId(deploymentId)

    client.deleteDeployment(request)
  }

  def getDeployments(restApiId: RestApiId) = Try {
    val request = new GetDeploymentsRequest()
      .withRestApiId(restApiId)

    client.getDeployments(request)
  }

  def printDeployments(restApiId: RestApiId) = {
    for {
      l <- getDeployments(restApiId)
    } yield {
      val p = CliFormatter(
        restApiId,
        "Created Date"  -> 30,
        "Deployment Id" -> 15,
        "Description"   -> 30
      ).print3(l.getItems.sortBy(d => d.getCreatedDate.getTime).reverse map { d =>
        (d.getCreatedDate.toString, d.getId, d.getDescription)
      }: _*)
      println(p)
    }
  }

  def deleteDeployments(restApiId: RestApiId) =
    for {
      l <- getDeployments(restApiId)
      _ <- Try(l.getItems foreach (i => deleteDeployment(restApiId, i.getId).get))
    } yield l

  def createStage(restApiId: RestApiId,
                  stageName: StageName,
                  deploymentId: DeploymentId,
                  description: Option[StageDescription],
                  variables: Option[StageVariables]) = Try {
    val request = new CreateStageRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)
      .withDeploymentId(deploymentId)
    description.foreach(request.setDescription)
    variables.foreach(v => request.setVariables(v.asJava))

    client.createStage(request)
  }

  def getStage(restApiId: RestApiId, stageName: StageName) = Try {
    val request = new GetStageRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)

    toOpt(client.getStage(request))
  }

  def updateStage(restApiId: RestApiId, stageName: StageName, deploymentId: DeploymentId) = Try {
    val po = new PatchOperation()
      .withOp(Op.Replace)
      .withPath("/deploymentId")
      .withValue(deploymentId)

    val request = new UpdateStageRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)
      .withPatchOperations(po)

    client.updateStage(request)
  }

  def deleteStage(restApiId: RestApiId, stageName: StageName) = Try {
    val request = new DeleteStageRequest()
      .withRestApiId(restApiId)
      .withStageName(stageName)

    client.deleteStage(request)
  }

  def createOrUpdateStage(restApiId: RestApiId,
                          stageName: StageName,
                          deploymentId: DeploymentId,
                          description: Option[StageDescription],
                          variables: Option[StageVariables]) = {
    for {
      sOp <- getStage(restApiId, stageName)
      res <- Try {
        sOp map { s =>
          updateStage(
            restApiId = restApiId,
            stageName = stageName,
            deploymentId = deploymentId
          ).get.getDeploymentId
        } getOrElse {
          createStage(restApiId = restApiId,
                      stageName = stageName,
                      deploymentId = deploymentId,
                      description = description,
                      variables = variables).get.getDeploymentId
        }
      }
    } yield res
  }

  def getStages(restApiId: RestApiId) = Try {
    val request = new GetStagesRequest()
      .withRestApiId(restApiId)

    client.getStages(request)
  }

  def printStages(restApiId: RestApiId) = {
    for {
      l <- getStages(restApiId)
    } yield {
      val p = CliFormatter(
        restApiId,
        "Stage Name"        -> 20,
        "Last Updated Date" -> 30,
        "Deployment Id"     -> 15,
        "Description"       -> 30
      ).print4(l.getItem.map { s =>
        (s.getStageName, s.getLastUpdatedDate.toString, s.getDeploymentId, s.getDescription)
      }: _*)
      println(p)
    }
  }

  def deleteStages(restApiId: RestApiId) =
    for {
      l <- getStages(restApiId)
      _ <- Try(l.getItem.foreach(i => deleteStage(restApiId, i.getStageName).get))
    } yield l

  @tailrec
  private def getAllResources(restApiId: RestApiId,
                              position: Option[String],
                              resources: Seq[Resource]): Seq[Resource] =
    position match {
      case Some(p) =>
        val request = new GetResourcesRequest().withRestApiId(restApiId).withPosition(p)
        val result  = client.getResources(request)
        val items   = result.getItems.asScala
        getAllResources(restApiId, Option(result.getPosition), resources ++ items)

      case _ => resources
    }

  def getResources(restApiId: RestApiId): Try[Seq[Resource]] = Try {
    val request = new GetResourcesRequest()
      .withRestApiId(restApiId)

    val result = client.getResources(request)
    val items  = result.getItems.asScala
    getAllResources(restApiId, Option(result.getPosition), items)
  }

  def printResources(restApiId: RestApiId) = {
    lazy val getResourceMethodKeys = (r: Resource) =>
      Option(r.getResourceMethods) map (_.keys.mkString(",")) getOrElse ""

    for {
      l <- getResources(restApiId)
    } yield {
      val p = CliFormatter(
        restApiId,
        "Resource Id"   -> 15,
        "Resource Path" -> 50,
        "Method Keys"   -> 30
      ).print3(l.sortBy(_.getPath) map { r =>
        (r.getId, r.getPath, getResourceMethodKeys(r))
      }: _*)
      println(p)
    }
  }

  def deleteResource(restApiId: RestApiId, resourceId: ResourceId) = Try {
    val request = new DeleteResourceRequest()
      .withRestApiId(restApiId)
      .withResourceId(resourceId)

    client.deleteResource(request)
  }

  def deleteResources(restApiId: RestApiId) = {
    for {
      l <- getResources(restApiId)
      _ <- Try(l.filter(_.getPath != "/").foreach { r =>
        try {
          deleteResource(restApiId, r.getId).get
        } catch {
          case e: com.amazonaws.services.apigateway.model.NotFoundException =>
        }
      })
    } yield l
  }

  def getAuthorizers(restApiId: RestApiId) = Try {
    val request = new GetAuthorizersRequest()
      .withRestApiId(restApiId)

    client.getAuthorizers(request)
  }

  def printAuthorizers(restApiId: RestApiId) =
    for {
      l <- getAuthorizers(restApiId)
    } yield {
      val p = CliFormatter(
        s"Rest API Authorizers: $restApiId",
        "ID"   -> 15,
        "Name" -> 40,
        "URI"  -> 150
      ).print3(l.getItems map { d =>
        (d.getId, d.getName, d.getAuthorizerUri)
      }: _*)
      println(p)
    }

}
case class AWSApiGatewayRestApi(regionName: String) extends AWSApiGatewayRestApiWrapper
