package com.orbit.shared.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.orbit.shared.error.BaseCode;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ApiErrorCodesGroup.class)
public @interface ApiErrorCodes {

    Class<? extends BaseCode> enumClass();

    String[] includes();
}
