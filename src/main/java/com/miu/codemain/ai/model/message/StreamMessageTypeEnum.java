package com.miu.codemain.ai.model.message;

import lombok.Getter; // Lombok注解，自动生成所有字段的getter方法

/**
 * 流式消息类型枚举
 */
@Getter
public enum StreamMessageTypeEnum {

    //    三个枚举常量，每个常量包含两个参数
    AI_RESPONSE("ai_response", "AI响应"),
    TOOL_REQUEST("tool_request", "工具请求"),
    TOOL_EXECUTED("tool_executed", "工具执行结果");

    //    声明两个final字段，确保枚举实例的不可变性
    private final String value;
    private final String text;

    StreamMessageTypeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据值获取枚举
     */
    public static StreamMessageTypeEnum getEnumByValue(String value) {
        for (StreamMessageTypeEnum typeEnum : values()) {
            if (typeEnum.getValue().equals(value)) {
                return typeEnum;
            }
        }
        return null;
    }
}