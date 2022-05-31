package org.sunbird.job.dialcodecontextupdater.helpers

import com.datastax.driver.core.Row
import com.datastax.driver.core.querybuilder.QueryBuilder
import org.slf4j.LoggerFactory
import org.sunbird.job.Metrics
import org.sunbird.job.dialcodecontextupdater.domain.Event
import org.sunbird.job.dialcodecontextupdater.task.DialcodeContextUpdaterConfig
import org.sunbird.job.dialcodecontextupdater.util.DialcodeContextUpdaterConstants
import org.sunbird.job.exception.{APIException, ServerException}
import org.sunbird.job.util._

import java.io.File
import java.nio.file.Paths
import java.util


trait DialcodeContextUpdater {

	private[this] val logger = LoggerFactory.getLogger(classOf[DialcodeContextUpdater])

	def updateContext(config: DialcodeContextUpdaterConfig, event: Event, httpUtil: HttpUtil, neo4JUtil: Neo4JUtil, cassandraUtil: CassandraUtil, metrics: Metrics): Row = {
		val identifier = event.identifier
		val dialcode = event.dialcode
		if(identifier.nonEmpty && event.action.equalsIgnoreCase("dialcode-context-update")) {
			// Compute contextInfo and update cassandra with contextInfo
			// 1. Read search fields from the context json file
			// 2. Call Search API with dialcode and mode=collection
			// 3. Fetch unit and rootNode output from the response
			// 4. compose contextInfo and upsert to cassandra
			val identifierObj = searchContent("", identifier, config.identifierSearchFields, config, httpUtil)

			println("DialcodeContextUpdater:: updateContext:: config.contextMapFilePath: " + config.contextMapFilePath)
			val primaryCategory = identifierObj.getOrElse("primaryCategory","").asInstanceOf[String]
			val contextMap: Map[String, AnyRef] = getContextMapFields(primaryCategory.toLowerCase.replaceAll(" ","_"), config.contextMapFilePath)
			val contextSearchFields: List[String] = fetchFieldsFromMap(contextMap).distinct.filter(rec => rec.forall(_.isLetterOrDigit))
			println("DialcodeContextUpdater:: updateContext:: searchFields: " + contextSearchFields)

			val contextInfoSearchData =	searchContent(dialcode, identifier, contextSearchFields, config, httpUtil)
			println("DialcodeContextUpdater:: updateContext:: contextInfoSearchData: " + ScalaJsonUtil.serialize(contextInfoSearchData))

			// Filter for necessary fields
			val filteredParentInfo = if(contextInfoSearchData.contains("parentInfo")) contextInfoSearchData.getOrElse("parentInfo", Map.empty[String, AnyRef]).asInstanceOf[Map[String, AnyRef]].filter(rec => contextSearchFields.contains(rec._1)) else Map.empty[String, AnyRef]
			val inputContextData = contextInfoSearchData.filter(rec => contextSearchFields.contains(rec._1))
			println("DialcodeContextUpdater:: updateContext:: filteredParentInfo: " + filteredParentInfo)
			println("DialcodeContextUpdater:: updateContext:: inputContextData: " + inputContextData)

			val finalFilteredData = if(filteredParentInfo!=null && filteredParentInfo.nonEmpty) inputContextData + ("parentInfo" -> filteredParentInfo) else inputContextData
			println("DialcodeContextUpdater:: updateContext:: finalFilteredData: " + finalFilteredData)
			updateCassandra(config, dialcode, ScalaJsonUtil.serialize(finalFilteredData), cassandraUtil, metrics)
		} else {
			// check if contextInfo is available in metadata. If Yes, update to null
			val row = readDialCodeFromCassandra(config, dialcode, cassandraUtil)
			if(row.getString("metadata") != null && row.getString("metadata").nonEmpty)
				updateCassandra(config, dialcode, null, cassandraUtil, metrics)
		}

		readDialCodeFromCassandra(config, dialcode, cassandraUtil)
	}

//	private def getCollectionHierarchy(identifier: String, config: DialcodeContextUpdaterConfig)(implicit cassandraUtil: CassandraUtil): Option[Map[String, AnyRef]] = {
//		val selectWhere: Select.Where = QueryBuilder.select().all()
//			.from(config.cassandraHierarchyKeyspace, config.cassandraHierarchyTable).where()
//		selectWhere.and(QueryBuilder.eq("identifier", identifier))
//		val row = cassandraUtil.findOne(selectWhere.toString)
//		if (null != row) {
//			val data: Map[String, AnyRef] = ScalaJsonUtil.deserialize[Map[String, AnyRef]](row.getString("hierarchy"))
//			Option(data)
//		} else Option(Map.empty[String, AnyRef])
//	}

	def updateCassandra(config: DialcodeContextUpdaterConfig, dialcode: String, contextInfo: String, cassandraUtil: CassandraUtil, metrics: Metrics): Unit = {
		val updateQuery: String = QueryBuilder.update(config.cassandraDialCodeKeyspace, config.cassandraDialCodeTable)
			.`with`(QueryBuilder.set("metadata", contextInfo))
			.where(QueryBuilder.eq("identifier", dialcode)).toString
		cassandraUtil.upsert(updateQuery)
		metrics.incCounter(config.dbHitEventCount)
	}

	def readDialCodeFromCassandra(config: DialcodeContextUpdaterConfig, dialcode: String, cassandraUtil: CassandraUtil): Row = {
		val selectQuery = QueryBuilder.select().all().from(config.cassandraDialCodeKeyspace, config.cassandraDialCodeTable)
		selectQuery.where.and(QueryBuilder.eq("identifier", dialcode))
		cassandraUtil.findOne(selectQuery.toString)
	}

	def getContextMapFields(contextType: String, contextMapFileURL: String): Map[String, AnyRef] = {
		try {
			val contextMap: Map[String, AnyRef] = ScalaJsonUtil.deserialize[Map[String, AnyRef]](ComplexJsonCompiler.createConsolidatedSchema(contextMapFileURL, contextType))
			println("DialcodeContextUpdater:: getContextSearchFields:: contextMap: " + contextMap)
			contextMap
		} catch {
			case e: Exception =>
				throw new ServerException("ERR_CONTEXT_MAP_READING","Error in reading context map file: " + e.getMessage)
		}
	}

	def fetchFieldsFromMap(inputMap: Map[String, AnyRef]): List[String] = {
		inputMap.flatMap(rec => {
			rec._2 match {
				case strVal: String => List(strVal)
				case objVal: Map[String, AnyRef] => fetchFieldsFromMap(objVal)
			}
		}).toList
	}


	private def searchContent(dialcode: String, identifier: String, searchFields: List[String], config: DialcodeContextUpdaterConfig, httpUtil: HttpUtil): Map[String, AnyRef] = {
		val reqMap = new java.util.HashMap[String, AnyRef]() {
			put(DialcodeContextUpdaterConstants.REQUEST, new java.util.HashMap[String, AnyRef]() {
				put(DialcodeContextUpdaterConstants.FILTERS, new java.util.HashMap[String, AnyRef]() {
					put(DialcodeContextUpdaterConstants.VISIBILITY, new util.ArrayList[String]() {
						add("Default")
						add("Parent")
					})
					put(DialcodeContextUpdaterConstants.IDENTIFIER, identifier)
					if(dialcode.nonEmpty) {
						put(DialcodeContextUpdaterConstants.DIALCODES, new util.ArrayList[String](){
							add(dialcode)
						})
					}
				})
				put(DialcodeContextUpdaterConstants.FIELDS, searchFields.toArray[String])
				put(DialcodeContextUpdaterConstants.SEARCH_MODE, config.searchMode)
			})
		}

		val requestUrl = s"${config.searchServiceBaseUrl}/v3/search"
		logger.info("DialcodeContextUpdater :: searchContent :: Search Content requestUrl: " + requestUrl)
		logger.info("DialcodeContextUpdater :: searchContent :: Search Content reqMap: " + reqMap)
		val httpResponse = httpUtil.post(requestUrl, JSONUtil.serialize(reqMap))
		if (httpResponse.status == 200) {
			val response = JSONUtil.deserialize[Map[String, AnyRef]](httpResponse.body)
			val result = response.getOrElse("result", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
			val content = result.getOrElse("content", List[Map[String,AnyRef]]()).asInstanceOf[List[Map[String,AnyRef]]]
			val collections = result.getOrElse("collections", List[Map[String,AnyRef]]()).asInstanceOf[List[Map[String,AnyRef]]]
			val count = result.getOrElse("count", 0).asInstanceOf[Int]
			if(count>0) {
				if(collections.isEmpty) {
					content.head
				} else {
					content.head + ("parentInfo" -> collections.filter(record => !record.contains("origin")).head)
				}
			} else {
				throw new ServerException("ERR_DIAL_CONTENT_NOT_FOUND", "No content linking was found for dialcode: " + dialcode + " - to identifier: " + identifier)
			}
		} else {
			throw new ServerException("ERR_API_CALL", "Invalid Response received while searching content for dialcode: " + dialcode)
		}
	}

}
