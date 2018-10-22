package org.vmmagic.pragma;

import org.vmmagic.Pragma;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@Pragma
public @interface RDMA { }
