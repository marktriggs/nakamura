<?xml version="1.0" encoding="UTF-8"?>
<!--
Copyright (c) 2006-2007, OmniTI Computer Consulting, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
     copyright notice, this list of conditions and the following
     disclaimer in the documentation and/or other materials provided
     with the distribution.
   * Neither the name OmniTI Computer Consulting, Inc. nor the names
     of its contributors may be used to endorse or promote products
     derived from this software without specific prior written
     permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
-->
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:template match="ResmonResults">
<html>
<head>
    <title>Resmon Results</title>
    <link rel="stylesheet" type="text/css" href="/system/resmon/resmon.css"/>
</head>
<body>
    <ul class="navbar">
        <li><a href="/">List all checks</a></li>
        <li><a href="/BAD">List all checks that are BAD</a></li>
        <li><a href="/WARNING">List all checks that are WARNING</a></li>
        <li><a href="/OK">List all checks that are OK</a></li>
    </ul>
    <p>
    Total checks:
    <xsl:value-of select="count(ResmonResult)"/>
    </p>
    <xsl:for-each select="ResmonResult">
        <xsl:sort select="@module"/>
        <xsl:sort select="@service"/>
        <div class="item">
                <xsl:attribute name="class">
                    item <xsl:value-of select="state"/>
                </xsl:attribute>
            <div class="info">
                Last check: <xsl:value-of select="last_runtime_seconds"/>
                /
                Last updated: <xsl:value-of select="last_update"/>
            </div>
            <h1>
                <a>
                    <xsl:attribute name="href">
                        /<xsl:value-of select="@module"/>
                    </xsl:attribute>
                    <xsl:value-of select="@module"/>
                </a>`<a>
                    <xsl:attribute name="href">
                        /<xsl:value-of select="@module"/>/<xsl:value-of select="@service"/>
                    </xsl:attribute>
                    <xsl:value-of select="@service"/>
                </a>
                -
                <xsl:value-of select="state"/>:
                <xsl:value-of select="metric[attribute::name='message']"/>
            </h1>
            <xsl:if test="count(metric[attribute::name!='message']) &gt; 0">
                <ul>
                    <xsl:for-each select="metric[attribute::name!='message']">
                        <xsl:sort select="@name"/>
                        <li><xsl:value-of select="@name"/> =
                        <xsl:value-of select="."/></li>
                    </xsl:for-each>
                </ul>
            </xsl:if>
        </div>
    </xsl:for-each>
</body>
</html>
</xsl:template>
</xsl:stylesheet>
