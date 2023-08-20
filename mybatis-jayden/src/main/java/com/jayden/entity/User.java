package com.jayden.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author linguobin
 */
@Data
public class User implements Serializable {

    private Long id ;
    private String userName ;
    private Date createTime;

}
