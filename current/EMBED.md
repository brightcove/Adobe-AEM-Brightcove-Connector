# Brightcove Maven Embedding and Deployment Steps

## Add the repository to the main `POM.xml` file (either a private repository or a public one depending upon where the connector package is hosted).

'''xml

'''

## Add a dependency on the connector package in the main `POM.xml`.

'''xml
<dependency>
    <groupId>com.brightcove</groupId>
    <artifactId>com.brightcove.connector</artifactId>
    <version>5.7.0</version>
    <type>zip</type>
</dependency>
'''

## Add a dependency to the connector package in the `ALL` project’s `POM.xml`.

'''xml
<dependency>
    <groupId>com.brightcove</groupId>
    <artifactId>com.brightcove.connector</artifactId>
    <type>zip</type>
</dependency>
'''

## The connector package should be embedded in the deployment. Add the package as an embedded dependency to the `ALL` project package.

'''xml
<embedded>
    <groupId>com.brightcove</groupId>
    <artifactId>com.brightcove.connector</artifactId>
    <type>zip</type>
    <target>/apps/brightcove-vendor-packages/application/install</target>
</embedded>
'''

### The connector will be installed on AEM Cloud with the next AEM Cloud Pipeline Build.