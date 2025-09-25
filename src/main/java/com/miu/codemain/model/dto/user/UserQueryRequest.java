package com.miu.codemain.model.dto.user;


import com.miu.codemain.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

//import java.io.Serial;
import java.io.Serializable;

@EqualsAndHashCode(callSuper = false)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 简介
     */
    private String userProfile;

    /**
     * 用户角色：user/admin/ban
     */
    private String userRole;

//    @Serial
    private static final long serialVersionUID = 1L;
}