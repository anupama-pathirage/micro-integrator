<?xml version="1.0" encoding="ISO-8859-1"?>

<!--
  ~ Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com) All Rights Reserved.
  ~
  ~ WSO2 LLC. licenses this file to you under the Apache License,
  ~ Version 2.0 (the "License"); you may not use this file except
  ~ in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~ http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing,
  ~ software distributed under the License is distributed on an
  ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~ KIND, either express or implied. See the License for the
  ~ specific language governing permissions and limitations
  ~ under the License.
  ~
  -->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:fn="http://www.w3.org/2005/02/xpath-functions"
                xmlns:m0="http://services.samples"
                exclude-result-prefixes="m0 fn">
    <xsl:output method="xml" omit-xml-declaration="yes" indent="yes"/>

    <xsl:template match="/">
        <xsl:apply-templates select="//m0:CheckPriceRequest"/>
    </xsl:template>

    <xsl:template match="m0:CheckPriceRequest">

        <m:getQuote xmlns:m="http://services.samples">
            <m:request>
                <m:symbol>
                    <xsl:value-of select="m0:Code"/>
                </m:symbol>
            </m:request>
        </m:getQuote>

    </xsl:template>
</xsl:stylesheet>
