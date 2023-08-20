/**
 *    Copyright ${license.git.copyrightYears} the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder.xml;

import java.io.InputStream;
import java.io.Reader;
import java.util.Properties;
import javax.sql.DataSource;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.datasource.DataSourceFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.loader.ProxyFactory;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.io.VFS;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.ReflectorFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.AutoMappingBehavior;
import org.apache.ibatis.session.AutoMappingUnknownColumnBehavior;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class XMLConfigBuilder extends BaseBuilder {

  private boolean parsed;
  private final XPathParser parser;
  private String environment;
  private final ReflectorFactory localReflectorFactory = new DefaultReflectorFactory();

  public XMLConfigBuilder(Reader reader) {
    this(reader, null, null);
  }

  public XMLConfigBuilder(Reader reader, String environment) {
    this(reader, environment, null);
  }

  public XMLConfigBuilder(Reader reader, String environment, Properties props) {
    this(new XPathParser(reader, true, props, new XMLMapperEntityResolver()), environment, props);
  }

  public XMLConfigBuilder(InputStream inputStream) {
    this(inputStream, null, null);
  }

  public XMLConfigBuilder(InputStream inputStream, String environment) {
    this(inputStream, environment, null);
  }

  /**
   * 创建一个用于解析xml配置的构建器对象
   * @param inputStream 传入进来的xml的配置
   * @param environment 环境变量
   * @param props:用于保存从xml中解析出来的属性
   */
  public XMLConfigBuilder(InputStream inputStream, String environment, Properties props) {
    /**
     * 该方法做了二个事情
     * 第一件事情:创建XPathParser 解析器对象,在这里会把
     * 把mybatis-config.xml解析出一个Document对象
     * 第二节事情:调用重写的构造函数来构建我XMLConfigBuilder
     */
    this(new XPathParser(inputStream, true, props, new XMLMapperEntityResolver()), environment, props);
  }


  private XMLConfigBuilder(XPathParser parser, String environment, Properties props) {
    /**
     * 调用父类的BaseBuilder的构造方法:给
     * configuration赋值
     * typeAliasRegistry别名注册器赋值
     * TypeHandlerRegistry赋值
     */
    super(new Configuration());
    ErrorContext.instance().resource("SQL Mapper Configuration");
    /**
     * 把props绑定到configuration的props属性上
     */
    this.configuration.setVariables(props);
    this.parsed = false;
    this.environment = environment;
    this.parser = parser;
  }

  public Configuration parse() {
    // 判断是否已经解析过了，如果解析过了就抛出异常
    if (parsed) {
      throw new BuilderException("Each XMLConfigBuilder can only be used once.");
    }
    parsed = true;

    // 解析mybatis-config.xml的<configuration>节点
    parseConfiguration(parser.evalNode("/configuration"));
    return configuration;
  }

  /**
   * 参数会传入一个XNode对象,这个XNode对象就是<configuration>节点
   *
   * configuration节点的结构如下，就是整个mybatis-config.xml的内容
   <configuration>
   <properties resource="db.properties"/>
   <settings>
   <setting name="mapUnderscoreToCamelCase" value="true"/>
   </settings>
   <environments default="development">
   <environment id="development">
   <transactionManager type="JDBC"/>
   <dataSource type="POOLED">
   <property name="driver" value="${mysql.driverClass}"/>
   <property name="url" value="${mysql.jdbcUrl}"/>
   <property name="username" value="${mysql.user}"/>
   <property name="password" value="${mysql.password}"/>
   </dataSource>
   </environment>
   </environments>
   <mappers>
   <package name="com.jayden.mapper"/>
   </mappers>
   </configuration>
   */
  private void parseConfiguration(XNode root) {
    try {
      /**
       * 解析properties节点
       *
       * <properties resource="db.properties"></properties>
       *
       * 解析配置文件，将解析后的内容设置到configuration的variables字段
       */
      propertiesElement(root.evalNode("properties"));

      /**
       * settings节点
       *
       * <settings>
             <setting name="mapUnderscoreToCamelCase" value="true"/>
         </settings>
       *
       * 将settings解析到：configuration的vfsImpl字段中
       */
      Properties settings = settingsAsProperties(root.evalNode("settings"));
      loadCustomVfs(settings);

      /**
       * 指定 MyBatis 所用日志的具体实现，未指定时将自动查找，官网给出的默认顺序如下：
       * SLF4J | LOG4J | LOG4J2 | JDK_LOGGING | COMMONS_LOGGING | STDOUT_LOGGING | NO_LOGGING
       * 解析到configuration的logImpl字段中
       */
      loadCustomLogImpl(settings);

      /**
       * typeAliases节点
       *
       * <typeAliases>
           <typeAlias alias="Author" type="cn.jayden.pojo.Author"/>
        </typeAliases>
        <typeAliases>
           <package name="cn.jayden.pojo"/>
        </typeAliases>
       *
       * 解析到configuration的typeAliasRegistry.typeAliases字段中
       */
      typeAliasesElement(root.evalNode("typeAliases"));

      /**
       * plugins节点
       *
         Executor (update, query, flushStatements, commit, rollback, getTransaction, close, isClosed)
         ParameterHandler (getParameterObject, setParameters)
         ResultSetHandler (handleResultSets, handleOutputParameters)
         StatementHandler (prepare, parameterize, batch, update, query)
       *
       * 解析到configuration的interceptorChain.interceptors字段中
       */
      pluginElement(root.evalNode("plugins"));

      /**
       * objectFactory节点
       *
       * <objectFactory type="org.mybatis.example.ExampleObjectFactory">
       *   <property name="someProperty" value="100"/>
       * </objectFactory>
       *
       * 解析到configuration的objectFactory字段中
       */
      objectFactoryElement(root.evalNode("objectFactory"));

      /**
       * objectWrapperFactory节点
       *
       * <objectWrapperFactory type="org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory"/>
       *
       * 解析到configuration的objectWrapperFactory字段中
       */
      objectWrapperFactoryElement(root.evalNode("objectWrapperFactory"));

      /**
       * reflectorFactory节点
       *
       * <reflectorFactory type="org.apache.ibatis.reflection.DefaultReflectorFactory"/>
       *
       * 解析到configuration的reflectorFactory字段中
       */
      reflectorFactoryElement(root.evalNode("reflectorFactory"));

      // 如果配置文件中没有配置settings节点，那么就使用默认的配置，默认开启一级缓存就是在这里设置的
      settingsElement(settings);

      /**
       * 解析mybatis环境
         <environments default="dev">
           <environment id="dev">
             <transactionManager type="JDBC"/>
             <dataSource type="POOLED">
             <property name="driver" value="${jdbc.driver}"/>
             <property name="url" value="${jdbc.url}"/>
             <property name="username" value="root"/>
             <property name="password" value="root"/>
             </dataSource>
           </environment>

         <environment id="test">
           <transactionManager type="JDBC"/>
           <dataSource type="POOLED">
           <property name="driver" value="${jdbc.driver}"/>
           <property name="url" value="${jdbc.url}"/>
           <property name="username" value="root"/>
           <property name="password" value="root"/>
           </dataSource>
         </environment>
       </environments>
       *
       *  解析到configuration的environment字段中
       *  在集成spring情况下由 spring-mybatis提供数据源 和事务工厂
       */
      environmentsElement(root.evalNode("environments"));

      /**
       * 解析数据库厂商
       *     <databaseIdProvider type="DB_VENDOR">
                <property name="SQL Server" value="sqlserver"/>
                <property name="DB2" value="db2"/>
                <property name="Oracle" value="oracle" />
                <property name="MySql" value="mysql" />
             </databaseIdProvider>
       *  解析到configuration的databaseId中
       */
      databaseIdProviderElement(root.evalNode("databaseIdProvider"));

      /**
       * 解析类型处理器节点
       *
       * <typeHandlers>
            <typeHandler handler="org.mybatis.example.ExampleTypeHandler"/>
          </typeHandlers>

       *   解析到configuration的typeHandlerRegistry.typeHandlerMap字段中
       */
      typeHandlerElement(root.evalNode("typeHandlers"));
      // TODO：至此为止，mybatis-config.xml文件中的所有配置都已经解析完毕

      // TODO：这里开始解析mapper.xml文件
      /**
       *
       resource：来注册class类路径下的
       url:来指定磁盘下的或者网络资源的
       class:
       若注册Mapper不带xml文件的,这里可以直接注册
       若注册的Mapper带xml文件的，需要把xml文件和mapper文件同名 同路径

       <mappers>
          <mapper resource="mybatis/mapper/EmployeeMapper.xml"/>
          <mapper class="com.jayden.mapper.DeptMapper"></mapper>
          <package name="com.jayden.mapper"></package>
       </mappers>

       * package 1.解析mapper接口 解析到：org.apache.ibatis.session.Configuration#mapperRegistry.knownMappers
       */
      mapperElement(root.evalNode("mappers"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing SQL Mapper Configuration. Cause: " + e, e);
    }
  }

  /**
   * 返回一个Properties（继承HashTable）对象，该对象中存储了mybatis的配置信息
   */
  private Properties settingsAsProperties(XNode context) {
    if (context == null) {
      return new Properties();
    }
    Properties props = context.getChildrenAsProperties();
    MetaClass metaConfig = MetaClass.forClass(Configuration.class, localReflectorFactory);
    for (Object key : props.keySet()) {
      if (!metaConfig.hasSetter(String.valueOf(key))) {
        throw new BuilderException("The setting " + key + " is not known.  Make sure you spelled it correctly (case sensitive).");
      }
    }
    return props;
  }

  private void loadCustomVfs(Properties props) throws ClassNotFoundException {
    String value = props.getProperty("vfsImpl");
    if (value != null) {
      String[] clazzes = value.split(",");
      for (String clazz : clazzes) {
        if (!clazz.isEmpty()) {
          @SuppressWarnings("unchecked")
          Class<? extends VFS> vfsImpl = (Class<? extends VFS>)Resources.classForName(clazz);
          configuration.setVfsImpl(vfsImpl);
        }
      }
    }
  }

  private void loadCustomLogImpl(Properties props) {
    Class<? extends Log> logImpl = resolveClass(props.getProperty("logImpl"));
    configuration.setLogImpl(logImpl);
  }

  private void typeAliasesElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        // mybatis中配置几种类型别名的方式在这里解析
        if ("package".equals(child.getName())) {
          String typeAliasPackage = child.getStringAttribute("name");
          configuration.getTypeAliasRegistry().registerAliases(typeAliasPackage);
        } else {
          String alias = child.getStringAttribute("alias");
          String type = child.getStringAttribute("type");
          try {
            Class<?> clazz = Resources.classForName(type);
            if (alias == null) {
              typeAliasRegistry.registerAlias(clazz);
            } else {
              typeAliasRegistry.registerAlias(alias, clazz);
            }
          } catch (ClassNotFoundException e) {
            throw new BuilderException("Error registering typeAlias for '" + alias + "'. Cause: " + e, e);
          }
        }
      }
    }
  }

  private void pluginElement(XNode parent) throws Exception {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        String interceptor = child.getStringAttribute("interceptor");
        Properties properties = child.getChildrenAsProperties();
        Interceptor interceptorInstance = (Interceptor) resolveClass(interceptor).getDeclaredConstructor().newInstance();
        interceptorInstance.setProperties(properties);
        // 设置拦截器到configuration中
        configuration.addInterceptor(interceptorInstance);
      }
    }
  }

  private void objectFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties properties = context.getChildrenAsProperties();
      ObjectFactory factory = (ObjectFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(properties);
      configuration.setObjectFactory(factory);
    }
  }

  private void objectWrapperFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ObjectWrapperFactory factory = (ObjectWrapperFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setObjectWrapperFactory(factory);
    }
  }

  private void reflectorFactoryElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      ReflectorFactory factory = (ReflectorFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      configuration.setReflectorFactory(factory);
    }
  }

  /**
   * 解析properties节点
   * 示例节点信息写法为 <properties resource="db.properties">
   */
  private void propertiesElement(XNode context) throws Exception {
    if (context != null) {
      // 获取子节点的属性
      Properties defaults = context.getChildrenAsProperties();

      // 获取properties标签的resource属性和url属性，这两个属性不能同时存在。
      // 根据resource属性或url属性加载属性配置文件，并将属性文件中的属性添加到defaults中
      String resource = context.getStringAttribute("resource");
      String url = context.getStringAttribute("url");
      if (resource != null && url != null) {
        throw new BuilderException("The properties element cannot specify both a URL and a resource based property file reference.  Please specify one or the other.");
      }

      /**
       * defaults继承了Hashtable，存放db.properties中的key-value键值对，如下：
       * "mysql.driverClass" -> "com.mysql.jdbc.Driver"
       * "mysql.jdbcUrl" -> "jdbc:mysql://localhost:3306/jayden?characterEncoding=utf8"
       * "mysql.user" -> "root"
       * "mysql.password" -> "root"
       */
      if (resource != null) {
        defaults.putAll(Resources.getResourceAsProperties(resource));
      } else if (url != null) {
        defaults.putAll(Resources.getUrlAsProperties(url));
      }

      // 判断configuration中的Variables是否为空，如果不为空，则将defaults中的属性添加到Variables中
      Properties vars = configuration.getVariables();
      if (vars != null) {
        defaults.putAll(vars);
      }
      parser.setVariables(defaults);

      // 将properties标签中获取的信息设置到configuration中
      configuration.setVariables(defaults);
    }
  }

  private void settingsElement(Properties props) {
    configuration.setAutoMappingBehavior(AutoMappingBehavior.valueOf(props.getProperty("autoMappingBehavior", "PARTIAL")));
    configuration.setAutoMappingUnknownColumnBehavior(AutoMappingUnknownColumnBehavior.valueOf(props.getProperty("autoMappingUnknownColumnBehavior", "NONE")));
    configuration.setCacheEnabled(booleanValueOf(props.getProperty("cacheEnabled"), true));
    configuration.setProxyFactory((ProxyFactory) createInstance(props.getProperty("proxyFactory")));
    configuration.setLazyLoadingEnabled(booleanValueOf(props.getProperty("lazyLoadingEnabled"), false));
    configuration.setAggressiveLazyLoading(booleanValueOf(props.getProperty("aggressiveLazyLoading"), false));
    configuration.setMultipleResultSetsEnabled(booleanValueOf(props.getProperty("multipleResultSetsEnabled"), true));
    configuration.setUseColumnLabel(booleanValueOf(props.getProperty("useColumnLabel"), true));
    configuration.setUseGeneratedKeys(booleanValueOf(props.getProperty("useGeneratedKeys"), false));
    configuration.setDefaultExecutorType(ExecutorType.valueOf(props.getProperty("defaultExecutorType", "SIMPLE")));
    configuration.setDefaultStatementTimeout(integerValueOf(props.getProperty("defaultStatementTimeout"), null));
    configuration.setDefaultFetchSize(integerValueOf(props.getProperty("defaultFetchSize"), null));
    configuration.setDefaultResultSetType(resolveResultSetType(props.getProperty("defaultResultSetType")));
    configuration.setMapUnderscoreToCamelCase(booleanValueOf(props.getProperty("mapUnderscoreToCamelCase"), false));
    configuration.setSafeRowBoundsEnabled(booleanValueOf(props.getProperty("safeRowBoundsEnabled"), false));
    configuration.setLocalCacheScope(LocalCacheScope.valueOf(props.getProperty("localCacheScope", "SESSION")));
    configuration.setJdbcTypeForNull(JdbcType.valueOf(props.getProperty("jdbcTypeForNull", "OTHER")));
    configuration.setLazyLoadTriggerMethods(stringSetValueOf(props.getProperty("lazyLoadTriggerMethods"), "equals,clone,hashCode,toString"));
    configuration.setSafeResultHandlerEnabled(booleanValueOf(props.getProperty("safeResultHandlerEnabled"), true));
    configuration.setDefaultScriptingLanguage(resolveClass(props.getProperty("defaultScriptingLanguage")));
    configuration.setDefaultEnumTypeHandler(resolveClass(props.getProperty("defaultEnumTypeHandler")));
    configuration.setCallSettersOnNulls(booleanValueOf(props.getProperty("callSettersOnNulls"), false));
    configuration.setUseActualParamName(booleanValueOf(props.getProperty("useActualParamName"), true));
    configuration.setReturnInstanceForEmptyRow(booleanValueOf(props.getProperty("returnInstanceForEmptyRow"), false));
    configuration.setLogPrefix(props.getProperty("logPrefix"));
    configuration.setConfigurationFactory(resolveClass(props.getProperty("configurationFactory")));
  }

  private void environmentsElement(XNode context) throws Exception {
    if (context != null) {
      if (environment == null) {
        environment = context.getStringAttribute("default");
      }
      for (XNode child : context.getChildren()) {
        String id = child.getStringAttribute("id");
        if (isSpecifiedEnvironment(id)) {
          TransactionFactory txFactory = transactionManagerElement(child.evalNode("transactionManager"));
          DataSourceFactory dsFactory = dataSourceElement(child.evalNode("dataSource"));
          DataSource dataSource = dsFactory.getDataSource();
          Environment.Builder environmentBuilder = new Environment.Builder(id)
              .transactionFactory(txFactory)
              .dataSource(dataSource);
          configuration.setEnvironment(environmentBuilder.build());
        }
      }
    }
  }

  private void databaseIdProviderElement(XNode context) throws Exception {
    DatabaseIdProvider databaseIdProvider = null;
    if (context != null) {
      String type = context.getStringAttribute("type");
      // awful patch to keep backward compatibility
      if ("VENDOR".equals(type)) {
        type = "DB_VENDOR";
      }
      Properties properties = context.getChildrenAsProperties();
      databaseIdProvider = (DatabaseIdProvider) resolveClass(type).getDeclaredConstructor().newInstance();
      databaseIdProvider.setProperties(properties);
    }
    Environment environment = configuration.getEnvironment();
    if (environment != null && databaseIdProvider != null) {
      String databaseId = databaseIdProvider.getDatabaseId(environment.getDataSource());
      configuration.setDatabaseId(databaseId);
    }
  }

  private TransactionFactory transactionManagerElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      TransactionFactory factory = (TransactionFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a TransactionFactory.");
  }

  private DataSourceFactory dataSourceElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type");
      Properties props = context.getChildrenAsProperties();
      DataSourceFactory factory = (DataSourceFactory) resolveClass(type).getDeclaredConstructor().newInstance();
      factory.setProperties(props);
      return factory;
    }
    throw new BuilderException("Environment declaration requires a DataSourceFactory.");
  }

  private void typeHandlerElement(XNode parent) {
    if (parent != null) {
      for (XNode child : parent.getChildren()) {
        if ("package".equals(child.getName())) {
          String typeHandlerPackage = child.getStringAttribute("name");
          typeHandlerRegistry.register(typeHandlerPackage);
        } else {
          String javaTypeName = child.getStringAttribute("javaType");
          String jdbcTypeName = child.getStringAttribute("jdbcType");
          String handlerTypeName = child.getStringAttribute("handler");
          Class<?> javaTypeClass = resolveClass(javaTypeName);
          JdbcType jdbcType = resolveJdbcType(jdbcTypeName);
          Class<?> typeHandlerClass = resolveClass(handlerTypeName);
          if (javaTypeClass != null) {
            if (jdbcType == null) {
              typeHandlerRegistry.register(javaTypeClass, typeHandlerClass);
            } else {
              typeHandlerRegistry.register(javaTypeClass, jdbcType, typeHandlerClass);
            }
          } else {
            typeHandlerRegistry.register(typeHandlerClass);
          }
        }
      }
    }
  }

  private void mapperElement(XNode parent) throws Exception {
    if (parent != null) {
      // 遍历处理mappers下的mapper节点
      for (XNode child : parent.getChildren()) {
        /**
         * mybatis有四种注册mapper的方式会在这里处理
         */
        if ("package".equals(child.getName())) {
          // 这里会得到包名com.jayden.mapper
          String mapperPackage = child.getStringAttribute("name");
          configuration.addMappers(mapperPackage);
        } else {
          /**
           * <mapper resource="mybatis/mapper/EmployeeMapper.xml"/>
           */
          String resource = child.getStringAttribute("resource");

          /**
           * <mapper url="D:/mapper/EmployeeMapper.xml"/>
           */
          String url = child.getStringAttribute("url");

          /**
           * <mapper class="com.jayden.mapper.DeptMapper"></mapper>
           */
          String mapperClass = child.getStringAttribute("class");

          /**
           * 得mappers节点只配置了
           * <mapper resource="mybatis/mapper/EmployeeMapper.xml"/>
           */
          if (resource != null && url == null && mapperClass == null) {
            ErrorContext.instance().resource(resource);
            /**
             * 把文件读取出一个流
             */
            InputStream inputStream = Resources.getResourceAsStream(resource);

            /**
             * 创建读取XmlMapper构建器对象,用于来解析mapper.xml文件
             */
            // TODO XMLMapperBuilder
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments());

            /**
             * 真正解析mapper.xml配置文件(说白了就是来解析sql)
             */
            mapperParser.parse();
          } else if (resource == null && url != null && mapperClass == null) {
            ErrorContext.instance().resource(url);
            InputStream inputStream = Resources.getUrlAsStream(url);
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(inputStream, configuration, url, configuration.getSqlFragments());
            mapperParser.parse();
          } else if (resource == null && url == null && mapperClass != null) {
            Class<?> mapperInterface = Resources.classForName(mapperClass);
            configuration.addMapper(mapperInterface);
          } else {
            throw new BuilderException("A mapper element may only specify a url, resource or class, but not more than one.");
          }
        }
      }
    }
  }

  private boolean isSpecifiedEnvironment(String id) {
    if (environment == null) {
      throw new BuilderException("No environment specified.");
    } else if (id == null) {
      throw new BuilderException("Environment requires an id attribute.");
    } else if (environment.equals(id)) {
      return true;
    }
    return false;
  }

}
