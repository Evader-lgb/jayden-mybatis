package com.jayden;

import com.jayden.entity.User;
import com.jayden.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.Reader;

/**
 * @author linguobin
 */
@Slf4j
public class MybatisSourceTestStart {
    public static void main(String[] args) {
        String resource = "mybatis-config.xml";

        // 使用 try-with-resources 语句，保证 Reader 和 SqlSession 能够被正确关闭
        try (
            Reader reader = Resources.getResourceAsReader(resource);
            // TODO（Mybatis解析配置文件入口）：解析配置文件，创建 SqlSessionFactory
            // TODO SqlSessionFactoryBuilder
            SqlSession session = new SqlSessionFactoryBuilder().build(reader).openSession())
        {
            UserMapper mapper = session.getMapper(UserMapper.class);

            try {
                // 方式一执行查询
//                User user1 = session.selectOne("com.jayden.mapper.UserMapper.selectById", 1);

                // 方式二执行查询
                User user2 = mapper.selectById(1L);
                session.commit();
            } catch (Exception e) {
                // 回滚事务
                session.rollback();
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
