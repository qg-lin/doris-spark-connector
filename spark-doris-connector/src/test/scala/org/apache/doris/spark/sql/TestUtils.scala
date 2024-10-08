// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.spark.sql

import org.apache.doris.spark.cfg.{ConfigurationOptions, PropertiesSettings, Settings}
import org.apache.doris.spark.exception.DorisException
import org.apache.doris.spark.rest.PartitionDefinition
import org.apache.spark.sql.jdbc.JdbcDialects
import org.apache.spark.sql.sources._
import org.hamcrest.core.StringStartsWith.startsWith
import org.junit._
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

class TestUtils extends ExpectedExceptionTest {
  private lazy val logger = LoggerFactory.getLogger(classOf[TestUtils])

  @Test
  def testCompileFilter(): Unit = {
    val dialect = JdbcDialects.get("")
    val inValueLengthLimit = 5

    val equalFilter = EqualTo("left", 5)
    val notEqualFilter = Not(EqualTo("left", 5))
    val greaterThanFilter = GreaterThan("left", 5)
    val greaterThanOrEqualFilter = GreaterThanOrEqual("left", 5)
    val lessThanFilter = LessThan("left", 5)
    val lessThanOrEqualFilter = LessThanOrEqual("left", 5)
    val validInFilter = In("left", Array(1, 2, 3, 4))
    val emptyInFilter = In("left", Array.empty)
    val invalidInFilter = In("left", Array(1, 2, 3, 4, 5))
    val notInFilter = Not(In("left", Array(1, 2, 3)))
    val isNullFilter = IsNull("left")
    val isNotNullFilter = IsNotNull("left")
    val validAndFilter = And(equalFilter, greaterThanFilter)
    val invalidAndFilter = And(equalFilter, invalidInFilter)
    val validOrFilter = Or(equalFilter, greaterThanFilter)
    val invalidOrFilter = Or(equalFilter, invalidInFilter)
    val stringContainsFilter = StringContains("left", "right")
    val notStringContainsFilter = Not(StringContains("left", "right"))
    val stringEndsWithFilter = StringEndsWith("left", "right")
    val notStringEndsWithFilter = Not(StringEndsWith("left", "right"))
    val stringStartsWithFilter = StringStartsWith("left", "right")
    val notStringStartsWithFilter = Not(StringStartsWith("left", "right"))

    Assert.assertEquals("`left` = 5", Utils.compileFilter(equalFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` != 5", Utils.compileFilter(notEqualFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` > 5", Utils.compileFilter(greaterThanFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` >= 5", Utils.compileFilter(greaterThanOrEqualFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` < 5", Utils.compileFilter(lessThanFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` <= 5", Utils.compileFilter(lessThanOrEqualFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` in (1, 2, 3, 4)", Utils.compileFilter(validInFilter, dialect, inValueLengthLimit).get)
    Assert.assertTrue(Utils.compileFilter(emptyInFilter, dialect, inValueLengthLimit).isEmpty)
    Assert.assertTrue(Utils.compileFilter(invalidInFilter, dialect, inValueLengthLimit).isEmpty)
    Assert.assertEquals("`left` not in (1, 2, 3)", Utils.compileFilter(notInFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` is null", Utils.compileFilter(isNullFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` is not null", Utils.compileFilter(isNotNullFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("(`left` = 5) and (`left` > 5)",
      Utils.compileFilter(validAndFilter, dialect, inValueLengthLimit).get)
    Assert.assertTrue(Utils.compileFilter(invalidAndFilter, dialect, inValueLengthLimit).isEmpty)
    Assert.assertEquals("(`left` = 5) or (`left` > 5)",
      Utils.compileFilter(validOrFilter, dialect, inValueLengthLimit).get)
    Assert.assertTrue(Utils.compileFilter(invalidOrFilter, dialect, inValueLengthLimit).isEmpty)
    Assert.assertEquals("`left` like '%right%'", Utils.compileFilter(stringContainsFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` not like '%right%'", Utils.compileFilter(notStringContainsFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` like '%right'", Utils.compileFilter(stringEndsWithFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` not like '%right'", Utils.compileFilter(notStringEndsWithFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` like 'right%'", Utils.compileFilter(stringStartsWithFilter, dialect, inValueLengthLimit).get)
    Assert.assertEquals("`left` not like 'right%'", Utils.compileFilter(notStringStartsWithFilter, dialect, inValueLengthLimit).get)
  }

  @Test
  def testParams(): Unit = {
    val parameters1 = Map(
      ConfigurationOptions.DORIS_TABLE_IDENTIFIER -> "a.b",
      "test_underline" -> "x_y",
      "user" -> "user",
      "password" -> "password"
    )
    val result1 = Utils.params(parameters1, logger)
    Assert.assertEquals("a.b", result1(ConfigurationOptions.DORIS_TABLE_IDENTIFIER))
    Assert.assertEquals("x_y", result1("doris.test.underline"))
    Assert.assertEquals("user", result1("doris.request.auth.user"))
    Assert.assertEquals("password", result1("doris.request.auth.password"))


    val parameters2 = Map(
      ConfigurationOptions.TABLE_IDENTIFIER -> "a.b"
    )
    val result2 = Utils.params(parameters2, logger)
    Assert.assertEquals("a.b", result2(ConfigurationOptions.DORIS_TABLE_IDENTIFIER))

    val parameters3 = Map(
      ConfigurationOptions.DORIS_PASSWORD -> "a.b"
    )
    thrown.expect(classOf[DorisException])
    thrown.expectMessage(startsWith(s"${ConfigurationOptions.DORIS_PASSWORD} cannot use in Doris Datasource,"))
    Utils.params(parameters3, logger)

    val parameters4 = Map(
      ConfigurationOptions.DORIS_USER -> "a.b"
    )
    thrown.expect(classOf[DorisException])
    thrown.expectMessage(startsWith(s"${ConfigurationOptions.DORIS_USER} cannot use in Doris Datasource,"))
    Utils.params(parameters4, logger)

    val parameters5 = Map(
      ConfigurationOptions.DORIS_REQUEST_AUTH_PASSWORD -> "a.b"
    )
    thrown.expect(classOf[DorisException])
    thrown.expectMessage(
      startsWith(s"${ConfigurationOptions.DORIS_REQUEST_AUTH_PASSWORD} cannot use in Doris Datasource,"))
    Utils.params(parameters5, logger)

    val parameters6 = Map(
      ConfigurationOptions.DORIS_REQUEST_AUTH_USER -> "a.b"
    )
    thrown.expect(classOf[DorisException])
    thrown.expectMessage(startsWith(s"${ConfigurationOptions.DORIS_REQUEST_AUTH_USER} cannot use in Doris Datasource,"))
    Utils.params(parameters6, logger)
  }

  @Test
  def testGenerateQueryStatement(): Unit = {

    val readColumns = Array[String]("*")

    val partition = new PartitionDefinition("db", "tbl1", new PropertiesSettings(), "127.0.0.1:8060", Set[java.lang.Long](1L).asJava, "")
    Assert.assertEquals("SELECT * FROM `db`.`tbl1` TABLET(1)",
      Utils.generateQueryStatement(readColumns, Array[String](), Array[String](), "`db`.`tbl1`", "", Some(partition)))

    val readColumns1 = Array[String]("`c1`","`c2`","`c3`")

    val bitmapColumns = Array[String]("c2")
    val hllColumns = Array[String]("c3")

    val where = "c1 = 10"

    Assert.assertEquals("SELECT `c1`,'READ UNSUPPORTED' AS `c2`,'READ UNSUPPORTED' AS `c3` FROM `db`.`tbl1`  WHERE c1 = 10",
      Utils.generateQueryStatement(readColumns1, bitmapColumns, hllColumns, "`db`.`tbl1`", where))

  }

}
