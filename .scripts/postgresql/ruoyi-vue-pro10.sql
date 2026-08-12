--
-- PostgreSQL database dump
--

\restrict lHKTRHsiAYh9gijjAFrIL3pbtB0G71ugGq0SDGq6TzddFa2Xib2TZ96hG3qKwvu

-- Dumped from database version 18.4 (Debian 18.4-1.pgdg13+1)
-- Dumped by pg_dump version 18.4 (Debian 18.4-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

DROP DATABASE IF EXISTS "ruoyi-vue-pro20";
--
-- Name: ruoyi-vue-pro20; Type: DATABASE; Schema: -; Owner: -
--

CREATE DATABASE "ruoyi-vue-pro20" WITH TEMPLATE = template0 ENCODING = 'UTF8' LOCALE_PROVIDER = libc LOCALE = 'en_US.utf8';


\unrestrict lHKTRHsiAYh9gijjAFrIL3pbtB0G71ugGq0SDGq6TzddFa2Xib2TZ96hG3qKwvu
\encoding SQL_ASCII
\connect -reuse-previous=on "dbname='ruoyi-vue-pro20'"
\restrict lHKTRHsiAYh9gijjAFrIL3pbtB0G71ugGq0SDGq6TzddFa2Xib2TZ96hG3qKwvu

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: dataset_image_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dataset_image_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dataset_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dataset_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dataset_tag_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dataset_tag_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dataset_task_result_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dataset_task_result_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dataset_task_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dataset_task_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dataset_task_user_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dataset_task_user_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dataset_video_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.dataset_video_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: dual; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.dual (
);


--
-- Name: infra_api_access_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_api_access_log (
    id bigint NOT NULL,
    trace_id character varying(64) DEFAULT ''::character varying NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    application_name character varying(50) NOT NULL,
    request_method character varying(16) DEFAULT ''::character varying NOT NULL,
    request_url character varying(255) DEFAULT ''::character varying NOT NULL,
    request_params text,
    response_body text,
    user_ip character varying(50) NOT NULL,
    user_agent character varying(512) NOT NULL,
    operate_module character varying(50) DEFAULT NULL::character varying,
    operate_name character varying(50) DEFAULT NULL::character varying,
    operate_type smallint DEFAULT 0,
    begin_time timestamp without time zone NOT NULL,
    end_time timestamp without time zone NOT NULL,
    duration integer NOT NULL,
    result_code integer DEFAULT 0 NOT NULL,
    result_msg character varying(512) DEFAULT ''::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_api_access_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_api_access_log IS 'API 访问日志表';


--
-- Name: COLUMN infra_api_access_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.id IS '日志主键';


--
-- Name: COLUMN infra_api_access_log.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.trace_id IS '链路追踪编号';


--
-- Name: COLUMN infra_api_access_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.user_id IS '用户编号';


--
-- Name: COLUMN infra_api_access_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.user_type IS '用户类型';


--
-- Name: COLUMN infra_api_access_log.application_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.application_name IS '应用名';


--
-- Name: COLUMN infra_api_access_log.request_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.request_method IS '请求方法名';


--
-- Name: COLUMN infra_api_access_log.request_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.request_url IS '请求地址';


--
-- Name: COLUMN infra_api_access_log.request_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.request_params IS '请求参数';


--
-- Name: COLUMN infra_api_access_log.response_body; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.response_body IS '响应结果';


--
-- Name: COLUMN infra_api_access_log.user_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.user_ip IS '用户 IP';


--
-- Name: COLUMN infra_api_access_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.user_agent IS '浏览器 UA';


--
-- Name: COLUMN infra_api_access_log.operate_module; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.operate_module IS '操作模块';


--
-- Name: COLUMN infra_api_access_log.operate_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.operate_name IS '操作名';


--
-- Name: COLUMN infra_api_access_log.operate_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.operate_type IS '操作分类';


--
-- Name: COLUMN infra_api_access_log.begin_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.begin_time IS '开始请求时间';


--
-- Name: COLUMN infra_api_access_log.end_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.end_time IS '结束请求时间';


--
-- Name: COLUMN infra_api_access_log.duration; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.duration IS '执行时长';


--
-- Name: COLUMN infra_api_access_log.result_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.result_code IS '结果码';


--
-- Name: COLUMN infra_api_access_log.result_msg; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.result_msg IS '结果提示';


--
-- Name: COLUMN infra_api_access_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.creator IS '创建者';


--
-- Name: COLUMN infra_api_access_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.create_time IS '创建时间';


--
-- Name: COLUMN infra_api_access_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.updater IS '更新者';


--
-- Name: COLUMN infra_api_access_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.update_time IS '更新时间';


--
-- Name: COLUMN infra_api_access_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.deleted IS '是否删除';


--
-- Name: COLUMN infra_api_access_log.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_access_log.tenant_id IS '租户编号';


--
-- Name: infra_api_access_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_api_access_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_api_error_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_api_error_log (
    id integer NOT NULL,
    trace_id character varying(64) NOT NULL,
    user_id integer DEFAULT 0 NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    application_name character varying(50) NOT NULL,
    request_method character varying(16) NOT NULL,
    request_url character varying(255) NOT NULL,
    request_params character varying(8000) NOT NULL,
    user_ip character varying(50) NOT NULL,
    user_agent character varying(512) NOT NULL,
    exception_time timestamp without time zone NOT NULL,
    exception_name character varying(128) DEFAULT ''::character varying NOT NULL,
    exception_message text NOT NULL,
    exception_root_cause_message text NOT NULL,
    exception_stack_trace text NOT NULL,
    exception_class_name character varying(512) NOT NULL,
    exception_file_name character varying(512) NOT NULL,
    exception_method_name character varying(512) NOT NULL,
    exception_line_number integer NOT NULL,
    process_status smallint NOT NULL,
    process_time timestamp without time zone,
    process_user_id integer DEFAULT 0,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_api_error_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_api_error_log IS '系统异常日志';


--
-- Name: COLUMN infra_api_error_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.id IS '编号';


--
-- Name: COLUMN infra_api_error_log.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.trace_id IS '链路追踪编号
     *
     * 一般来说，通过链路追踪编号，可以将访问日志，错误日志，链路追踪日志，logger 打印日志等，结合在一起，从而进行排错。';


--
-- Name: COLUMN infra_api_error_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.user_id IS '用户编号';


--
-- Name: COLUMN infra_api_error_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.user_type IS '用户类型';


--
-- Name: COLUMN infra_api_error_log.application_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.application_name IS '应用名
     *
     * 目前读取 spring.application.name';


--
-- Name: COLUMN infra_api_error_log.request_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.request_method IS '请求方法名';


--
-- Name: COLUMN infra_api_error_log.request_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.request_url IS '请求地址';


--
-- Name: COLUMN infra_api_error_log.request_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.request_params IS '请求参数';


--
-- Name: COLUMN infra_api_error_log.user_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.user_ip IS '用户 IP';


--
-- Name: COLUMN infra_api_error_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.user_agent IS '浏览器 UA';


--
-- Name: COLUMN infra_api_error_log.exception_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_time IS '异常发生时间';


--
-- Name: COLUMN infra_api_error_log.exception_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_name IS '异常名
     *
     * {@link Throwable#getClass()} 的类全名';


--
-- Name: COLUMN infra_api_error_log.exception_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_message IS '异常导致的消息
     *
     * {@link cn.iocoder.common.framework.util.ExceptionUtil#getMessage(Throwable)}';


--
-- Name: COLUMN infra_api_error_log.exception_root_cause_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_root_cause_message IS '异常导致的根消息
     *
     * {@link cn.iocoder.common.framework.util.ExceptionUtil#getRootCauseMessage(Throwable)}';


--
-- Name: COLUMN infra_api_error_log.exception_stack_trace; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_stack_trace IS '异常的栈轨迹
     *
     * {@link cn.iocoder.common.framework.util.ExceptionUtil#getServiceException(Exception)}';


--
-- Name: COLUMN infra_api_error_log.exception_class_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_class_name IS '异常发生的类全名
     *
     * {@link StackTraceElement#getClassName()}';


--
-- Name: COLUMN infra_api_error_log.exception_file_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_file_name IS '异常发生的类文件
     *
     * {@link StackTraceElement#getFileName()}';


--
-- Name: COLUMN infra_api_error_log.exception_method_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_method_name IS '异常发生的方法名
     *
     * {@link StackTraceElement#getMethodName()}';


--
-- Name: COLUMN infra_api_error_log.exception_line_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.exception_line_number IS '异常发生的方法所在行
     *
     * {@link StackTraceElement#getLineNumber()}';


--
-- Name: COLUMN infra_api_error_log.process_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.process_status IS '处理状态';


--
-- Name: COLUMN infra_api_error_log.process_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.process_time IS '处理时间';


--
-- Name: COLUMN infra_api_error_log.process_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.process_user_id IS '处理用户编号';


--
-- Name: COLUMN infra_api_error_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.creator IS '创建者';


--
-- Name: COLUMN infra_api_error_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.create_time IS '创建时间';


--
-- Name: COLUMN infra_api_error_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.updater IS '更新者';


--
-- Name: COLUMN infra_api_error_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.update_time IS '更新时间';


--
-- Name: COLUMN infra_api_error_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.deleted IS '是否删除';


--
-- Name: COLUMN infra_api_error_log.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_api_error_log.tenant_id IS '租户编号';


--
-- Name: infra_api_error_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_api_error_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_codegen_column; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_codegen_column (
    id bigint NOT NULL,
    table_id bigint NOT NULL,
    column_name character varying(200) NOT NULL,
    data_type character varying(100) NOT NULL,
    column_comment character varying(500) NOT NULL,
    nullable boolean NOT NULL,
    primary_key boolean NOT NULL,
    ordinal_position integer NOT NULL,
    java_type character varying(32) NOT NULL,
    java_field character varying(64) NOT NULL,
    dict_type character varying(200) DEFAULT ''::character varying,
    example character varying(64) DEFAULT NULL::character varying,
    create_operation boolean NOT NULL,
    update_operation boolean NOT NULL,
    list_operation boolean NOT NULL,
    list_operation_condition character varying(32) DEFAULT '='::character varying NOT NULL,
    list_operation_result boolean NOT NULL,
    html_type character varying(32) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_codegen_column; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_codegen_column IS '代码生成表字段定义';


--
-- Name: COLUMN infra_codegen_column.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.id IS '编号';


--
-- Name: COLUMN infra_codegen_column.table_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.table_id IS '表编号';


--
-- Name: COLUMN infra_codegen_column.column_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.column_name IS '字段名';


--
-- Name: COLUMN infra_codegen_column.data_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.data_type IS '字段类型';


--
-- Name: COLUMN infra_codegen_column.column_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.column_comment IS '字段描述';


--
-- Name: COLUMN infra_codegen_column.nullable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.nullable IS '是否允许为空';


--
-- Name: COLUMN infra_codegen_column.primary_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.primary_key IS '是否主键';


--
-- Name: COLUMN infra_codegen_column.ordinal_position; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.ordinal_position IS '排序';


--
-- Name: COLUMN infra_codegen_column.java_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.java_type IS 'Java 属性类型';


--
-- Name: COLUMN infra_codegen_column.java_field; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.java_field IS 'Java 属性名';


--
-- Name: COLUMN infra_codegen_column.dict_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.dict_type IS '字典类型';


--
-- Name: COLUMN infra_codegen_column.example; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.example IS '数据示例';


--
-- Name: COLUMN infra_codegen_column.create_operation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.create_operation IS '是否为 Create 创建操作的字段';


--
-- Name: COLUMN infra_codegen_column.update_operation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.update_operation IS '是否为 Update 更新操作的字段';


--
-- Name: COLUMN infra_codegen_column.list_operation; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.list_operation IS '是否为 List 查询操作的字段';


--
-- Name: COLUMN infra_codegen_column.list_operation_condition; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.list_operation_condition IS 'List 查询操作的条件类型';


--
-- Name: COLUMN infra_codegen_column.list_operation_result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.list_operation_result IS '是否为 List 查询操作的返回字段';


--
-- Name: COLUMN infra_codegen_column.html_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.html_type IS '显示类型';


--
-- Name: COLUMN infra_codegen_column.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.creator IS '创建者';


--
-- Name: COLUMN infra_codegen_column.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.create_time IS '创建时间';


--
-- Name: COLUMN infra_codegen_column.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.updater IS '更新者';


--
-- Name: COLUMN infra_codegen_column.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.update_time IS '更新时间';


--
-- Name: COLUMN infra_codegen_column.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_column.deleted IS '是否删除';


--
-- Name: infra_codegen_column_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_codegen_column_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_codegen_table; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_codegen_table (
    id bigint NOT NULL,
    data_source_config_id bigint NOT NULL,
    scene smallint DEFAULT 1 NOT NULL,
    table_name character varying(200) DEFAULT ''::character varying NOT NULL,
    table_comment character varying(500) DEFAULT ''::character varying NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    module_name character varying(30) NOT NULL,
    business_name character varying(30) NOT NULL,
    class_name character varying(100) DEFAULT ''::character varying NOT NULL,
    class_comment character varying(50) NOT NULL,
    author character varying(50) NOT NULL,
    template_type smallint DEFAULT 1 NOT NULL,
    front_type smallint NOT NULL,
    parent_menu_id bigint,
    master_table_id bigint,
    sub_join_column_id bigint,
    sub_join_many boolean,
    tree_parent_column_id bigint,
    tree_name_column_id bigint,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_codegen_table; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_codegen_table IS '代码生成表定义';


--
-- Name: COLUMN infra_codegen_table.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.id IS '编号';


--
-- Name: COLUMN infra_codegen_table.data_source_config_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.data_source_config_id IS '数据源配置的编号';


--
-- Name: COLUMN infra_codegen_table.scene; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.scene IS '生成场景';


--
-- Name: COLUMN infra_codegen_table.table_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.table_name IS '表名称';


--
-- Name: COLUMN infra_codegen_table.table_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.table_comment IS '表描述';


--
-- Name: COLUMN infra_codegen_table.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.remark IS '备注';


--
-- Name: COLUMN infra_codegen_table.module_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.module_name IS '模块名';


--
-- Name: COLUMN infra_codegen_table.business_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.business_name IS '业务名';


--
-- Name: COLUMN infra_codegen_table.class_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.class_name IS '类名称';


--
-- Name: COLUMN infra_codegen_table.class_comment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.class_comment IS '类描述';


--
-- Name: COLUMN infra_codegen_table.author; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.author IS '作者';


--
-- Name: COLUMN infra_codegen_table.template_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.template_type IS '模板类型';


--
-- Name: COLUMN infra_codegen_table.front_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.front_type IS '前端类型';


--
-- Name: COLUMN infra_codegen_table.parent_menu_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.parent_menu_id IS '父菜单编号';


--
-- Name: COLUMN infra_codegen_table.master_table_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.master_table_id IS '主表的编号';


--
-- Name: COLUMN infra_codegen_table.sub_join_column_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.sub_join_column_id IS '子表关联主表的字段编号';


--
-- Name: COLUMN infra_codegen_table.sub_join_many; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.sub_join_many IS '主表与子表是否一对多';


--
-- Name: COLUMN infra_codegen_table.tree_parent_column_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.tree_parent_column_id IS '树表的父字段编号';


--
-- Name: COLUMN infra_codegen_table.tree_name_column_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.tree_name_column_id IS '树表的名字字段编号';


--
-- Name: COLUMN infra_codegen_table.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.creator IS '创建者';


--
-- Name: COLUMN infra_codegen_table.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.create_time IS '创建时间';


--
-- Name: COLUMN infra_codegen_table.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.updater IS '更新者';


--
-- Name: COLUMN infra_codegen_table.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.update_time IS '更新时间';


--
-- Name: COLUMN infra_codegen_table.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_codegen_table.deleted IS '是否删除';


--
-- Name: infra_codegen_table_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_codegen_table_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_config (
    id integer NOT NULL,
    category character varying(50) NOT NULL,
    type smallint NOT NULL,
    name character varying(100) DEFAULT ''::character varying NOT NULL,
    config_key character varying(100) DEFAULT ''::character varying NOT NULL,
    value character varying(500) DEFAULT ''::character varying NOT NULL,
    visible boolean NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_config IS '参数配置表';


--
-- Name: COLUMN infra_config.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.id IS '参数主键';


--
-- Name: COLUMN infra_config.category; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.category IS '参数分组';


--
-- Name: COLUMN infra_config.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.type IS '参数类型';


--
-- Name: COLUMN infra_config.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.name IS '参数名称';


--
-- Name: COLUMN infra_config.config_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.config_key IS '参数键名';


--
-- Name: COLUMN infra_config.value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.value IS '参数键值';


--
-- Name: COLUMN infra_config.visible; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.visible IS '是否可见';


--
-- Name: COLUMN infra_config.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.remark IS '备注';


--
-- Name: COLUMN infra_config.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.creator IS '创建者';


--
-- Name: COLUMN infra_config.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.create_time IS '创建时间';


--
-- Name: COLUMN infra_config.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.updater IS '更新者';


--
-- Name: COLUMN infra_config.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.update_time IS '更新时间';


--
-- Name: COLUMN infra_config.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_config.deleted IS '是否删除';


--
-- Name: infra_config_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_config_seq
    START WITH 13
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_data_source_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_data_source_config (
    id bigint NOT NULL,
    name character varying(100) DEFAULT ''::character varying NOT NULL,
    url character varying(1024) NOT NULL,
    username character varying(255) NOT NULL,
    password character varying(255) DEFAULT ''::character varying NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_data_source_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_data_source_config IS '数据源配置表';


--
-- Name: COLUMN infra_data_source_config.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.id IS '主键编号';


--
-- Name: COLUMN infra_data_source_config.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.name IS '参数名称';


--
-- Name: COLUMN infra_data_source_config.url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.url IS '数据源连接';


--
-- Name: COLUMN infra_data_source_config.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.username IS '用户名';


--
-- Name: COLUMN infra_data_source_config.password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.password IS '密码';


--
-- Name: COLUMN infra_data_source_config.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.creator IS '创建者';


--
-- Name: COLUMN infra_data_source_config.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.create_time IS '创建时间';


--
-- Name: COLUMN infra_data_source_config.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.updater IS '更新者';


--
-- Name: COLUMN infra_data_source_config.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.update_time IS '更新时间';


--
-- Name: COLUMN infra_data_source_config.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_data_source_config.deleted IS '是否删除';


--
-- Name: infra_data_source_config_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_data_source_config_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_file; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_file (
    id bigint NOT NULL,
    config_id bigint,
    name character varying(256) DEFAULT NULL::character varying,
    path character varying(512) NOT NULL,
    url character varying(1024) NOT NULL,
    type character varying(128) DEFAULT NULL::character varying,
    size integer NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_file; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_file IS '文件表';


--
-- Name: COLUMN infra_file.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.id IS '文件编号';


--
-- Name: COLUMN infra_file.config_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.config_id IS '配置编号';


--
-- Name: COLUMN infra_file.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.name IS '文件名';


--
-- Name: COLUMN infra_file.path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.path IS '文件路径';


--
-- Name: COLUMN infra_file.url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.url IS '文件 URL';


--
-- Name: COLUMN infra_file.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.type IS '文件类型';


--
-- Name: COLUMN infra_file.size; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.size IS '文件大小';


--
-- Name: COLUMN infra_file.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.creator IS '创建者';


--
-- Name: COLUMN infra_file.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.create_time IS '创建时间';


--
-- Name: COLUMN infra_file.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.updater IS '更新者';


--
-- Name: COLUMN infra_file.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.update_time IS '更新时间';


--
-- Name: COLUMN infra_file.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file.deleted IS '是否删除';


--
-- Name: infra_file_config; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_file_config (
    id bigint NOT NULL,
    name character varying(63) NOT NULL,
    storage smallint NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    master boolean NOT NULL,
    config character varying(4096) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_file_config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_file_config IS '文件配置表';


--
-- Name: COLUMN infra_file_config.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.id IS '编号';


--
-- Name: COLUMN infra_file_config.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.name IS '配置名';


--
-- Name: COLUMN infra_file_config.storage; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.storage IS '存储器';


--
-- Name: COLUMN infra_file_config.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.remark IS '备注';


--
-- Name: COLUMN infra_file_config.master; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.master IS '是否为主配置';


--
-- Name: COLUMN infra_file_config.config; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.config IS '存储配置';


--
-- Name: COLUMN infra_file_config.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.creator IS '创建者';


--
-- Name: COLUMN infra_file_config.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.create_time IS '创建时间';


--
-- Name: COLUMN infra_file_config.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.updater IS '更新者';


--
-- Name: COLUMN infra_file_config.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.update_time IS '更新时间';


--
-- Name: COLUMN infra_file_config.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_config.deleted IS '是否删除';


--
-- Name: infra_file_config_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_file_config_seq
    START WITH 23
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_file_content; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_file_content (
    id bigint NOT NULL,
    config_id bigint NOT NULL,
    path character varying(512) NOT NULL,
    content bytea NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_file_content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_file_content IS '文件表';


--
-- Name: COLUMN infra_file_content.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.id IS '编号';


--
-- Name: COLUMN infra_file_content.config_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.config_id IS '配置编号';


--
-- Name: COLUMN infra_file_content.path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.path IS '文件路径';


--
-- Name: COLUMN infra_file_content.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.content IS '文件内容';


--
-- Name: COLUMN infra_file_content.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.creator IS '创建者';


--
-- Name: COLUMN infra_file_content.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.create_time IS '创建时间';


--
-- Name: COLUMN infra_file_content.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.updater IS '更新者';


--
-- Name: COLUMN infra_file_content.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.update_time IS '更新时间';


--
-- Name: COLUMN infra_file_content.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_file_content.deleted IS '是否删除';


--
-- Name: infra_file_content_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_file_content_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_file_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_file_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_job; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_job (
    id bigint NOT NULL,
    name character varying(32) NOT NULL,
    status smallint NOT NULL,
    handler_name character varying(64) NOT NULL,
    handler_param character varying(255) DEFAULT NULL::character varying,
    cron_expression character varying(32) NOT NULL,
    retry_count integer DEFAULT 0 NOT NULL,
    retry_interval integer DEFAULT 0 NOT NULL,
    monitor_timeout integer DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_job; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_job IS '定时任务表';


--
-- Name: COLUMN infra_job.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.id IS '任务编号';


--
-- Name: COLUMN infra_job.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.name IS '任务名称';


--
-- Name: COLUMN infra_job.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.status IS '任务状态';


--
-- Name: COLUMN infra_job.handler_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.handler_name IS '处理器的名字';


--
-- Name: COLUMN infra_job.handler_param; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.handler_param IS '处理器的参数';


--
-- Name: COLUMN infra_job.cron_expression; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.cron_expression IS 'CRON 表达式';


--
-- Name: COLUMN infra_job.retry_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.retry_count IS '重试次数';


--
-- Name: COLUMN infra_job.retry_interval; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.retry_interval IS '重试间隔';


--
-- Name: COLUMN infra_job.monitor_timeout; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.monitor_timeout IS '监控超时时间';


--
-- Name: COLUMN infra_job.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.creator IS '创建者';


--
-- Name: COLUMN infra_job.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.create_time IS '创建时间';


--
-- Name: COLUMN infra_job.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.updater IS '更新者';


--
-- Name: COLUMN infra_job.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.update_time IS '更新时间';


--
-- Name: COLUMN infra_job.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job.deleted IS '是否删除';


--
-- Name: infra_job_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_job_log (
    id bigint NOT NULL,
    job_id bigint NOT NULL,
    handler_name character varying(64) NOT NULL,
    handler_param character varying(255) DEFAULT NULL::character varying,
    execute_index smallint DEFAULT 1 NOT NULL,
    begin_time timestamp without time zone NOT NULL,
    end_time timestamp without time zone,
    duration integer,
    status smallint NOT NULL,
    result character varying(4000) DEFAULT ''::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE infra_job_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_job_log IS '定时任务日志表';


--
-- Name: COLUMN infra_job_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.id IS '日志编号';


--
-- Name: COLUMN infra_job_log.job_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.job_id IS '任务编号';


--
-- Name: COLUMN infra_job_log.handler_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.handler_name IS '处理器的名字';


--
-- Name: COLUMN infra_job_log.handler_param; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.handler_param IS '处理器的参数';


--
-- Name: COLUMN infra_job_log.execute_index; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.execute_index IS '第几次执行';


--
-- Name: COLUMN infra_job_log.begin_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.begin_time IS '开始执行时间';


--
-- Name: COLUMN infra_job_log.end_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.end_time IS '结束执行时间';


--
-- Name: COLUMN infra_job_log.duration; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.duration IS '执行时长';


--
-- Name: COLUMN infra_job_log.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.status IS '任务状态';


--
-- Name: COLUMN infra_job_log.result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.result IS '结果数据';


--
-- Name: COLUMN infra_job_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.creator IS '创建者';


--
-- Name: COLUMN infra_job_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.create_time IS '创建时间';


--
-- Name: COLUMN infra_job_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.updater IS '更新者';


--
-- Name: COLUMN infra_job_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.update_time IS '更新时间';


--
-- Name: COLUMN infra_job_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_job_log.deleted IS '是否删除';


--
-- Name: infra_job_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_job_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_job_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.infra_job_seq
    START WITH 28
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: infra_platform_branding; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.infra_platform_branding (
    id bigint NOT NULL,
    platform_name character varying(100),
    platform_logo_file_id bigint,
    dashboard_title character varying(100),
    login_name character varying(100),
    login_logo_file_id bigint,
    login_form_title character varying(100),
    login_bg_light_file_id bigint,
    login_bg_dark_file_id bigint,
    default_platform_name character varying(100),
    default_platform_logo_file_id bigint,
    default_dashboard_title character varying(100),
    default_login_name character varying(100),
    default_login_logo_file_id bigint,
    default_login_form_title character varying(100),
    default_login_bg_light_file_id bigint,
    default_login_bg_dark_file_id bigint,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    CONSTRAINT infra_platform_branding_singleton CHECK ((id = 1))
);


--
-- Name: TABLE infra_platform_branding; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.infra_platform_branding IS '全平台唯一品牌配置表';


--
-- Name: COLUMN infra_platform_branding.platform_logo_file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.platform_logo_file_id IS '平台 Logo 对应 infra_file.id，空值使用内置默认图片';


--
-- Name: COLUMN infra_platform_branding.login_logo_file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.login_logo_file_id IS '登录 Logo 对应 infra_file.id，空值使用内置默认图片';


--
-- Name: COLUMN infra_platform_branding.login_bg_light_file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.login_bg_light_file_id IS '浅色登录背景对应 infra_file.id，空值使用内置默认图片';


--
-- Name: COLUMN infra_platform_branding.login_bg_dark_file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.login_bg_dark_file_id IS '深色登录背景对应 infra_file.id，空值使用内置默认图片';


--
-- Name: COLUMN infra_platform_branding.default_platform_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.default_platform_name IS '数据库维护的平台名称初始值，空值使用改造前内置默认值';


--
-- Name: COLUMN infra_platform_branding.default_platform_logo_file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.default_platform_logo_file_id IS '数据库维护的平台 Logo 初始文件 ID，空值使用改造前内置默认图片';


--
-- Name: COLUMN infra_platform_branding.default_dashboard_title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.default_dashboard_title IS '数据库维护的大屏标题初始值，空值使用改造前内置默认值';


--
-- Name: COLUMN infra_platform_branding.default_login_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.default_login_name IS '数据库维护的登录页名称初始值，空值使用改造前内置默认值';


--
-- Name: COLUMN infra_platform_branding.default_login_logo_file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.default_login_logo_file_id IS '数据库维护的登录 Logo 初始文件 ID，空值使用改造前内置默认图片';


--
-- Name: COLUMN infra_platform_branding.default_login_form_title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.default_login_form_title IS '数据库维护的登录表单标题初始值，空值使用改造前内置默认值';


--
-- Name: COLUMN infra_platform_branding.default_login_bg_light_file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.default_login_bg_light_file_id IS '数据库维护的浅色登录背景初始文件 ID，空值使用改造前内置默认图片';


--
-- Name: COLUMN infra_platform_branding.default_login_bg_dark_file_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.infra_platform_branding.default_login_bg_dark_file_id IS '数据库维护的深色登录背景初始文件 ID，空值使用改造前内置默认图片';


--
-- Name: model_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.model_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_server_quantify_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.model_server_quantify_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_server_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.model_server_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_server_test_image_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.model_server_test_image_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_server_test_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.model_server_test_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_server_test_video_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.model_server_test_video_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_server_video_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.model_server_video_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: model_type_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.model_type_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: qrtz_blob_triggers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_blob_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(190) NOT NULL,
    trigger_group character varying(190) NOT NULL,
    blob_data bytea
);


--
-- Name: qrtz_calendars; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_calendars (
    sched_name character varying(120) NOT NULL,
    calendar_name character varying(190) NOT NULL,
    calendar bytea NOT NULL
);


--
-- Name: qrtz_cron_triggers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_cron_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(190) NOT NULL,
    trigger_group character varying(190) NOT NULL,
    cron_expression character varying(120) NOT NULL,
    time_zone_id character varying(80) DEFAULT NULL::character varying
);


--
-- Name: qrtz_fired_triggers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_fired_triggers (
    sched_name character varying(120) NOT NULL,
    entry_id character varying(95) NOT NULL,
    trigger_name character varying(190) NOT NULL,
    trigger_group character varying(190) NOT NULL,
    instance_name character varying(190) NOT NULL,
    fired_time bigint NOT NULL,
    sched_time bigint NOT NULL,
    priority integer NOT NULL,
    state character varying(16) NOT NULL,
    job_name character varying(190) DEFAULT NULL::character varying,
    job_group character varying(190) DEFAULT NULL::character varying,
    is_nonconcurrent character varying(1) DEFAULT NULL::character varying,
    requests_recovery character varying(1) DEFAULT NULL::character varying
);


--
-- Name: qrtz_job_details; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_job_details (
    sched_name character varying(120) NOT NULL,
    job_name character varying(190) NOT NULL,
    job_group character varying(190) NOT NULL,
    description character varying(250) DEFAULT NULL::character varying,
    job_class_name character varying(250) NOT NULL,
    is_durable character varying(1) NOT NULL,
    is_nonconcurrent character varying(1) NOT NULL,
    is_update_data character varying(1) NOT NULL,
    requests_recovery character varying(1) NOT NULL,
    job_data bytea
);


--
-- Name: qrtz_locks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_locks (
    sched_name character varying(120) NOT NULL,
    lock_name character varying(40) NOT NULL
);


--
-- Name: qrtz_paused_trigger_grps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_paused_trigger_grps (
    sched_name character varying(120) NOT NULL,
    trigger_group character varying(190) NOT NULL
);


--
-- Name: qrtz_scheduler_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_scheduler_state (
    sched_name character varying(120) NOT NULL,
    instance_name character varying(190) NOT NULL,
    last_checkin_time bigint NOT NULL,
    checkin_interval bigint NOT NULL
);


--
-- Name: qrtz_simple_triggers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_simple_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(190) NOT NULL,
    trigger_group character varying(190) NOT NULL,
    repeat_count bigint NOT NULL,
    repeat_interval bigint NOT NULL,
    times_triggered bigint NOT NULL
);


--
-- Name: qrtz_simprop_triggers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_simprop_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(190) NOT NULL,
    trigger_group character varying(190) NOT NULL,
    str_prop_1 character varying(512) DEFAULT NULL::character varying,
    str_prop_2 character varying(512) DEFAULT NULL::character varying,
    str_prop_3 character varying(512) DEFAULT NULL::character varying,
    int_prop_1 integer,
    int_prop_2 integer,
    long_prop_1 bigint,
    long_prop_2 bigint,
    dec_prop_1 numeric(13,4) DEFAULT NULL::numeric,
    dec_prop_2 numeric(13,4) DEFAULT NULL::numeric,
    bool_prop_1 character varying(1) DEFAULT NULL::character varying,
    bool_prop_2 character varying(1) DEFAULT NULL::character varying
);


--
-- Name: qrtz_triggers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qrtz_triggers (
    sched_name character varying(120) NOT NULL,
    trigger_name character varying(190) NOT NULL,
    trigger_group character varying(190) NOT NULL,
    job_name character varying(190) NOT NULL,
    job_group character varying(190) NOT NULL,
    description character varying(250) DEFAULT NULL::character varying,
    next_fire_time bigint,
    prev_fire_time bigint,
    priority integer,
    trigger_state character varying(16) NOT NULL,
    trigger_type character varying(8) NOT NULL,
    start_time bigint NOT NULL,
    end_time bigint,
    calendar_name character varying(190) DEFAULT NULL::character varying,
    misfire_instr smallint,
    job_data bytea
);


--
-- Name: system_dept; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_dept (
    id bigint NOT NULL,
    name character varying(30) DEFAULT ''::character varying NOT NULL,
    parent_id bigint DEFAULT 0 NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    leader_user_id bigint,
    phone character varying(11) DEFAULT NULL::character varying,
    email character varying(50) DEFAULT NULL::character varying,
    status smallint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_dept; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_dept IS '部门表';


--
-- Name: COLUMN system_dept.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.id IS '部门id';


--
-- Name: COLUMN system_dept.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.name IS '部门名称';


--
-- Name: COLUMN system_dept.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.parent_id IS '父部门id';


--
-- Name: COLUMN system_dept.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.sort IS '显示顺序';


--
-- Name: COLUMN system_dept.leader_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.leader_user_id IS '负责人';


--
-- Name: COLUMN system_dept.phone; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.phone IS '联系电话';


--
-- Name: COLUMN system_dept.email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.email IS '邮箱';


--
-- Name: COLUMN system_dept.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.status IS '部门状态（0正常 1停用）';


--
-- Name: COLUMN system_dept.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.creator IS '创建者';


--
-- Name: COLUMN system_dept.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.create_time IS '创建时间';


--
-- Name: COLUMN system_dept.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.updater IS '更新者';


--
-- Name: COLUMN system_dept.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.update_time IS '更新时间';


--
-- Name: COLUMN system_dept.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.deleted IS '是否删除';


--
-- Name: COLUMN system_dept.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dept.tenant_id IS '租户编号';


--
-- Name: system_dept_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_dept_seq
    START WITH 114
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_dict_data; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_dict_data (
    id bigint NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    label character varying(100) DEFAULT ''::character varying NOT NULL,
    value character varying(100) DEFAULT ''::character varying NOT NULL,
    dict_type character varying(100) DEFAULT ''::character varying NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    color_type character varying(100) DEFAULT ''::character varying,
    css_class character varying(100) DEFAULT ''::character varying,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_dict_data; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_dict_data IS '字典数据表';


--
-- Name: COLUMN system_dict_data.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.id IS '字典编码';


--
-- Name: COLUMN system_dict_data.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.sort IS '字典排序';


--
-- Name: COLUMN system_dict_data.label; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.label IS '字典标签';


--
-- Name: COLUMN system_dict_data.value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.value IS '字典键值';


--
-- Name: COLUMN system_dict_data.dict_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.dict_type IS '字典类型';


--
-- Name: COLUMN system_dict_data.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.status IS '状态（0正常 1停用）';


--
-- Name: COLUMN system_dict_data.color_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.color_type IS '颜色类型';


--
-- Name: COLUMN system_dict_data.css_class; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.css_class IS 'css 样式';


--
-- Name: COLUMN system_dict_data.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.remark IS '备注';


--
-- Name: COLUMN system_dict_data.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.creator IS '创建者';


--
-- Name: COLUMN system_dict_data.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.create_time IS '创建时间';


--
-- Name: COLUMN system_dict_data.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.updater IS '更新者';


--
-- Name: COLUMN system_dict_data.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.update_time IS '更新时间';


--
-- Name: COLUMN system_dict_data.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_data.deleted IS '是否删除';


--
-- Name: system_dict_data_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_dict_data_seq
    START WITH 1537
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_dict_type; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_dict_type (
    id bigint NOT NULL,
    name character varying(100) DEFAULT ''::character varying NOT NULL,
    type character varying(100) DEFAULT ''::character varying NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    deleted_time timestamp without time zone
);


--
-- Name: TABLE system_dict_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_dict_type IS '字典类型表';


--
-- Name: COLUMN system_dict_type.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.id IS '字典主键';


--
-- Name: COLUMN system_dict_type.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.name IS '字典名称';


--
-- Name: COLUMN system_dict_type.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.type IS '字典类型';


--
-- Name: COLUMN system_dict_type.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.status IS '状态（0正常 1停用）';


--
-- Name: COLUMN system_dict_type.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.remark IS '备注';


--
-- Name: COLUMN system_dict_type.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.creator IS '创建者';


--
-- Name: COLUMN system_dict_type.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.create_time IS '创建时间';


--
-- Name: COLUMN system_dict_type.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.updater IS '更新者';


--
-- Name: COLUMN system_dict_type.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.update_time IS '更新时间';


--
-- Name: COLUMN system_dict_type.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.deleted IS '是否删除';


--
-- Name: COLUMN system_dict_type.deleted_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_dict_type.deleted_time IS '删除时间';


--
-- Name: system_dict_type_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_dict_type_seq
    START WITH 620
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_login_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_login_log (
    id bigint NOT NULL,
    log_type bigint NOT NULL,
    trace_id character varying(64) DEFAULT ''::character varying NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    username character varying(50) DEFAULT ''::character varying NOT NULL,
    result smallint NOT NULL,
    user_ip character varying(50) NOT NULL,
    user_agent character varying(512) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_login_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_login_log IS '系统访问记录';


--
-- Name: COLUMN system_login_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.id IS '访问ID';


--
-- Name: COLUMN system_login_log.log_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.log_type IS '日志类型';


--
-- Name: COLUMN system_login_log.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.trace_id IS '链路追踪编号';


--
-- Name: COLUMN system_login_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.user_id IS '用户编号';


--
-- Name: COLUMN system_login_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.user_type IS '用户类型';


--
-- Name: COLUMN system_login_log.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.username IS '用户账号';


--
-- Name: COLUMN system_login_log.result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.result IS '登陆结果';


--
-- Name: COLUMN system_login_log.user_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.user_ip IS '用户 IP';


--
-- Name: COLUMN system_login_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.user_agent IS '浏览器 UA';


--
-- Name: COLUMN system_login_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.creator IS '创建者';


--
-- Name: COLUMN system_login_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.create_time IS '创建时间';


--
-- Name: COLUMN system_login_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.updater IS '更新者';


--
-- Name: COLUMN system_login_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.update_time IS '更新时间';


--
-- Name: COLUMN system_login_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.deleted IS '是否删除';


--
-- Name: COLUMN system_login_log.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_login_log.tenant_id IS '租户编号';


--
-- Name: system_login_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_login_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_mail_account; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_mail_account (
    id bigint NOT NULL,
    mail character varying(255) NOT NULL,
    username character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    host character varying(255) NOT NULL,
    port integer NOT NULL,
    ssl_enable boolean DEFAULT false NOT NULL,
    starttls_enable boolean DEFAULT false NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_mail_account; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_mail_account IS '邮箱账号表';


--
-- Name: COLUMN system_mail_account.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.id IS '主键';


--
-- Name: COLUMN system_mail_account.mail; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.mail IS '邮箱';


--
-- Name: COLUMN system_mail_account.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.username IS '用户名';


--
-- Name: COLUMN system_mail_account.password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.password IS '密码';


--
-- Name: COLUMN system_mail_account.host; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.host IS 'SMTP 服务器域名';


--
-- Name: COLUMN system_mail_account.port; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.port IS 'SMTP 服务器端口';


--
-- Name: COLUMN system_mail_account.ssl_enable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.ssl_enable IS '是否开启 SSL';


--
-- Name: COLUMN system_mail_account.starttls_enable; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.starttls_enable IS '是否开启 STARTTLS';


--
-- Name: COLUMN system_mail_account.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.creator IS '创建者';


--
-- Name: COLUMN system_mail_account.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.create_time IS '创建时间';


--
-- Name: COLUMN system_mail_account.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.updater IS '更新者';


--
-- Name: COLUMN system_mail_account.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.update_time IS '更新时间';


--
-- Name: COLUMN system_mail_account.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_account.deleted IS '是否删除';


--
-- Name: system_mail_account_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_mail_account_seq
    START WITH 5
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_mail_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_mail_log (
    id bigint NOT NULL,
    user_id bigint,
    user_type smallint,
    to_mail character varying(255) NOT NULL,
    account_id bigint NOT NULL,
    from_mail character varying(255) NOT NULL,
    template_id bigint NOT NULL,
    template_code character varying(63) NOT NULL,
    template_nickname character varying(255) DEFAULT NULL::character varying,
    template_title character varying(255) NOT NULL,
    template_content character varying(10240) NOT NULL,
    template_params character varying(255) NOT NULL,
    send_status smallint DEFAULT 0 NOT NULL,
    send_time timestamp without time zone,
    send_message_id character varying(255) DEFAULT NULL::character varying,
    send_exception character varying(4096) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_mail_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_mail_log IS '邮件日志表';


--
-- Name: COLUMN system_mail_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.id IS '编号';


--
-- Name: COLUMN system_mail_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.user_id IS '用户编号';


--
-- Name: COLUMN system_mail_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.user_type IS '用户类型';


--
-- Name: COLUMN system_mail_log.to_mail; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.to_mail IS '接收邮箱地址';


--
-- Name: COLUMN system_mail_log.account_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.account_id IS '邮箱账号编号';


--
-- Name: COLUMN system_mail_log.from_mail; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.from_mail IS '发送邮箱地址';


--
-- Name: COLUMN system_mail_log.template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_id IS '模板编号';


--
-- Name: COLUMN system_mail_log.template_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_code IS '模板编码';


--
-- Name: COLUMN system_mail_log.template_nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_nickname IS '模版发送人名称';


--
-- Name: COLUMN system_mail_log.template_title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_title IS '邮件标题';


--
-- Name: COLUMN system_mail_log.template_content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_content IS '邮件内容';


--
-- Name: COLUMN system_mail_log.template_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.template_params IS '邮件参数';


--
-- Name: COLUMN system_mail_log.send_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.send_status IS '发送状态';


--
-- Name: COLUMN system_mail_log.send_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.send_time IS '发送时间';


--
-- Name: COLUMN system_mail_log.send_message_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.send_message_id IS '发送返回的消息 ID';


--
-- Name: COLUMN system_mail_log.send_exception; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.send_exception IS '发送异常';


--
-- Name: COLUMN system_mail_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.creator IS '创建者';


--
-- Name: COLUMN system_mail_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.create_time IS '创建时间';


--
-- Name: COLUMN system_mail_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.updater IS '更新者';


--
-- Name: COLUMN system_mail_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.update_time IS '更新时间';


--
-- Name: COLUMN system_mail_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_log.deleted IS '是否删除';


--
-- Name: system_mail_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_mail_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_mail_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_mail_template (
    id bigint NOT NULL,
    name character varying(63) NOT NULL,
    code character varying(63) NOT NULL,
    account_id bigint NOT NULL,
    nickname character varying(255) DEFAULT NULL::character varying,
    title character varying(255) NOT NULL,
    content character varying(10240) NOT NULL,
    params character varying(255) NOT NULL,
    status smallint NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_mail_template; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_mail_template IS '邮件模版表';


--
-- Name: COLUMN system_mail_template.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.id IS '编号';


--
-- Name: COLUMN system_mail_template.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.name IS '模板名称';


--
-- Name: COLUMN system_mail_template.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.code IS '模板编码';


--
-- Name: COLUMN system_mail_template.account_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.account_id IS '发送的邮箱账号编号';


--
-- Name: COLUMN system_mail_template.nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.nickname IS '发送人名称';


--
-- Name: COLUMN system_mail_template.title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.title IS '模板标题';


--
-- Name: COLUMN system_mail_template.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.content IS '模板内容';


--
-- Name: COLUMN system_mail_template.params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.params IS '参数数组';


--
-- Name: COLUMN system_mail_template.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.status IS '开启状态';


--
-- Name: COLUMN system_mail_template.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.remark IS '备注';


--
-- Name: COLUMN system_mail_template.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.creator IS '创建者';


--
-- Name: COLUMN system_mail_template.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.create_time IS '创建时间';


--
-- Name: COLUMN system_mail_template.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.updater IS '更新者';


--
-- Name: COLUMN system_mail_template.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.update_time IS '更新时间';


--
-- Name: COLUMN system_mail_template.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_mail_template.deleted IS '是否删除';


--
-- Name: system_mail_template_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_mail_template_seq
    START WITH 16
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_menu (
    id bigint NOT NULL,
    name character varying(50) NOT NULL,
    permission character varying(100) DEFAULT ''::character varying NOT NULL,
    type smallint NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    parent_id bigint DEFAULT 0 NOT NULL,
    path character varying(200) DEFAULT ''::character varying,
    icon character varying(100) DEFAULT '#'::character varying,
    component character varying(255) DEFAULT NULL::character varying,
    component_name character varying(255) DEFAULT NULL::character varying,
    status smallint DEFAULT 0 NOT NULL,
    visible boolean DEFAULT true NOT NULL,
    keep_alive boolean DEFAULT true NOT NULL,
    always_show boolean DEFAULT true NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_menu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_menu IS '菜单权限表';


--
-- Name: COLUMN system_menu.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.id IS '菜单ID';


--
-- Name: COLUMN system_menu.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.name IS '菜单名称';


--
-- Name: COLUMN system_menu.permission; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.permission IS '权限标识';


--
-- Name: COLUMN system_menu.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.type IS '菜单类型';


--
-- Name: COLUMN system_menu.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.sort IS '显示顺序';


--
-- Name: COLUMN system_menu.parent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.parent_id IS '父菜单ID';


--
-- Name: COLUMN system_menu.path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.path IS '路由地址';


--
-- Name: COLUMN system_menu.icon; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.icon IS '菜单图标';


--
-- Name: COLUMN system_menu.component; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.component IS '组件路径';


--
-- Name: COLUMN system_menu.component_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.component_name IS '组件名';


--
-- Name: COLUMN system_menu.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.status IS '菜单状态';


--
-- Name: COLUMN system_menu.visible; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.visible IS '是否可见';


--
-- Name: COLUMN system_menu.keep_alive; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.keep_alive IS '是否缓存';


--
-- Name: COLUMN system_menu.always_show; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.always_show IS '是否总是显示';


--
-- Name: COLUMN system_menu.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.creator IS '创建者';


--
-- Name: COLUMN system_menu.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.create_time IS '创建时间';


--
-- Name: COLUMN system_menu.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.updater IS '更新者';


--
-- Name: COLUMN system_menu.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.update_time IS '更新时间';


--
-- Name: COLUMN system_menu.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_menu.deleted IS '是否删除';


--
-- Name: system_menu_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_menu_seq
    START WITH 2758
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notice; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notice (
    id bigint NOT NULL,
    title character varying(50) NOT NULL,
    content text NOT NULL,
    type smallint NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_notice; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_notice IS '通知公告表';


--
-- Name: COLUMN system_notice.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.id IS '公告ID';


--
-- Name: COLUMN system_notice.title; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.title IS '公告标题';


--
-- Name: COLUMN system_notice.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.content IS '公告内容';


--
-- Name: COLUMN system_notice.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.type IS '公告类型（1通知 2公告）';


--
-- Name: COLUMN system_notice.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.status IS '公告状态（0正常 1关闭）';


--
-- Name: COLUMN system_notice.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.creator IS '创建者';


--
-- Name: COLUMN system_notice.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.create_time IS '创建时间';


--
-- Name: COLUMN system_notice.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.updater IS '更新者';


--
-- Name: COLUMN system_notice.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.update_time IS '更新时间';


--
-- Name: COLUMN system_notice.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.deleted IS '是否删除';


--
-- Name: COLUMN system_notice.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notice.tenant_id IS '租户编号';


--
-- Name: system_notice_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notice_seq
    START WITH 5
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notify_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notify_message (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    template_id bigint NOT NULL,
    template_code character varying(64) NOT NULL,
    template_nickname character varying(63) NOT NULL,
    template_content character varying(1024) NOT NULL,
    template_type integer NOT NULL,
    template_params character varying(255) NOT NULL,
    read_status boolean NOT NULL,
    read_time timestamp without time zone,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_notify_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_notify_message IS '站内信消息表';


--
-- Name: COLUMN system_notify_message.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.id IS '用户ID';


--
-- Name: COLUMN system_notify_message.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.user_id IS '用户id';


--
-- Name: COLUMN system_notify_message.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.user_type IS '用户类型';


--
-- Name: COLUMN system_notify_message.template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_id IS '模版编号';


--
-- Name: COLUMN system_notify_message.template_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_code IS '模板编码';


--
-- Name: COLUMN system_notify_message.template_nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_nickname IS '模版发送人名称';


--
-- Name: COLUMN system_notify_message.template_content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_content IS '模版内容';


--
-- Name: COLUMN system_notify_message.template_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_type IS '模版类型';


--
-- Name: COLUMN system_notify_message.template_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.template_params IS '模版参数';


--
-- Name: COLUMN system_notify_message.read_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.read_status IS '是否已读';


--
-- Name: COLUMN system_notify_message.read_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.read_time IS '阅读时间';


--
-- Name: COLUMN system_notify_message.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.creator IS '创建者';


--
-- Name: COLUMN system_notify_message.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.create_time IS '创建时间';


--
-- Name: COLUMN system_notify_message.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.updater IS '更新者';


--
-- Name: COLUMN system_notify_message.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.update_time IS '更新时间';


--
-- Name: COLUMN system_notify_message.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.deleted IS '是否删除';


--
-- Name: COLUMN system_notify_message.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_message.tenant_id IS '租户编号';


--
-- Name: system_notify_message_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notify_message_seq
    START WITH 11
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notify_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notify_template (
    id bigint NOT NULL,
    name character varying(63) NOT NULL,
    code character varying(64) NOT NULL,
    nickname character varying(255) NOT NULL,
    content character varying(1024) NOT NULL,
    type smallint NOT NULL,
    params character varying(255) DEFAULT NULL::character varying,
    status smallint NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_notify_template; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_notify_template IS '站内信模板表';


--
-- Name: COLUMN system_notify_template.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.id IS '主键';


--
-- Name: COLUMN system_notify_template.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.name IS '模板名称';


--
-- Name: COLUMN system_notify_template.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.code IS '模版编码';


--
-- Name: COLUMN system_notify_template.nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.nickname IS '发送人名称';


--
-- Name: COLUMN system_notify_template.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.content IS '模版内容';


--
-- Name: COLUMN system_notify_template.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.type IS '类型';


--
-- Name: COLUMN system_notify_template.params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.params IS '参数数组';


--
-- Name: COLUMN system_notify_template.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.status IS '状态';


--
-- Name: COLUMN system_notify_template.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.remark IS '备注';


--
-- Name: COLUMN system_notify_template.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.creator IS '创建者';


--
-- Name: COLUMN system_notify_template.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.create_time IS '创建时间';


--
-- Name: COLUMN system_notify_template.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.updater IS '更新者';


--
-- Name: COLUMN system_notify_template.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.update_time IS '更新时间';


--
-- Name: COLUMN system_notify_template.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_notify_template.deleted IS '是否删除';


--
-- Name: system_notify_template_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notify_template_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_access_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_access_token (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    user_info character varying(512) NOT NULL,
    access_token character varying(255) NOT NULL,
    refresh_token character varying(32) NOT NULL,
    client_id character varying(255) NOT NULL,
    scopes character varying(255) DEFAULT NULL::character varying,
    expires_time timestamp without time zone NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_access_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_access_token IS 'OAuth2 访问令牌';


--
-- Name: COLUMN system_oauth2_access_token.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.id IS '编号';


--
-- Name: COLUMN system_oauth2_access_token.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.user_id IS '用户编号';


--
-- Name: COLUMN system_oauth2_access_token.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.user_type IS '用户类型';


--
-- Name: COLUMN system_oauth2_access_token.user_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.user_info IS '用户信息';


--
-- Name: COLUMN system_oauth2_access_token.access_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.access_token IS '访问令牌';


--
-- Name: COLUMN system_oauth2_access_token.refresh_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.refresh_token IS '刷新令牌';


--
-- Name: COLUMN system_oauth2_access_token.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_access_token.scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.scopes IS '授权范围';


--
-- Name: COLUMN system_oauth2_access_token.expires_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.expires_time IS '过期时间';


--
-- Name: COLUMN system_oauth2_access_token.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_access_token.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_access_token.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_access_token.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_access_token.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.deleted IS '是否删除';


--
-- Name: COLUMN system_oauth2_access_token.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_access_token.tenant_id IS '租户编号';


--
-- Name: system_oauth2_access_token_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_access_token_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_approve; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_approve (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    client_id character varying(255) NOT NULL,
    scope character varying(255) DEFAULT ''::character varying NOT NULL,
    approved boolean DEFAULT false NOT NULL,
    expires_time timestamp without time zone NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_approve; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_approve IS 'OAuth2 批准表';


--
-- Name: COLUMN system_oauth2_approve.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.id IS '编号';


--
-- Name: COLUMN system_oauth2_approve.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.user_id IS '用户编号';


--
-- Name: COLUMN system_oauth2_approve.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.user_type IS '用户类型';


--
-- Name: COLUMN system_oauth2_approve.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_approve.scope; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.scope IS '授权范围';


--
-- Name: COLUMN system_oauth2_approve.approved; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.approved IS '是否接受';


--
-- Name: COLUMN system_oauth2_approve.expires_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.expires_time IS '过期时间';


--
-- Name: COLUMN system_oauth2_approve.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_approve.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_approve.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_approve.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_approve.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.deleted IS '是否删除';


--
-- Name: COLUMN system_oauth2_approve.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_approve.tenant_id IS '租户编号';


--
-- Name: system_oauth2_approve_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_approve_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_client; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_client (
    id bigint NOT NULL,
    client_id character varying(255) NOT NULL,
    secret character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    logo character varying(255) NOT NULL,
    description character varying(255) DEFAULT NULL::character varying,
    status smallint NOT NULL,
    access_token_validity_seconds integer NOT NULL,
    refresh_token_validity_seconds integer NOT NULL,
    redirect_uris character varying(255) NOT NULL,
    authorized_grant_types character varying(255) NOT NULL,
    scopes character varying(255) DEFAULT NULL::character varying,
    auto_approve_scopes character varying(255) DEFAULT NULL::character varying,
    authorities character varying(255) DEFAULT NULL::character varying,
    resource_ids character varying(255) DEFAULT NULL::character varying,
    additional_information character varying(4096) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_client; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_client IS 'OAuth2 客户端表';


--
-- Name: COLUMN system_oauth2_client.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.id IS '编号';


--
-- Name: COLUMN system_oauth2_client.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_client.secret; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.secret IS '客户端密钥';


--
-- Name: COLUMN system_oauth2_client.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.name IS '应用名';


--
-- Name: COLUMN system_oauth2_client.logo; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.logo IS '应用图标';


--
-- Name: COLUMN system_oauth2_client.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.description IS '应用描述';


--
-- Name: COLUMN system_oauth2_client.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.status IS '状态';


--
-- Name: COLUMN system_oauth2_client.access_token_validity_seconds; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.access_token_validity_seconds IS '访问令牌的有效期';


--
-- Name: COLUMN system_oauth2_client.refresh_token_validity_seconds; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.refresh_token_validity_seconds IS '刷新令牌的有效期';


--
-- Name: COLUMN system_oauth2_client.redirect_uris; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.redirect_uris IS '可重定向的 URI 地址';


--
-- Name: COLUMN system_oauth2_client.authorized_grant_types; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.authorized_grant_types IS '授权类型';


--
-- Name: COLUMN system_oauth2_client.scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.scopes IS '授权范围';


--
-- Name: COLUMN system_oauth2_client.auto_approve_scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.auto_approve_scopes IS '自动通过的授权范围';


--
-- Name: COLUMN system_oauth2_client.authorities; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.authorities IS '权限';


--
-- Name: COLUMN system_oauth2_client.resource_ids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.resource_ids IS '资源';


--
-- Name: COLUMN system_oauth2_client.additional_information; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.additional_information IS '附加信息';


--
-- Name: COLUMN system_oauth2_client.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_client.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_client.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_client.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_client.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_client.deleted IS '是否删除';


--
-- Name: system_oauth2_client_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_client_seq
    START WITH 43
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_code (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    code character varying(32) NOT NULL,
    client_id character varying(255) NOT NULL,
    scopes character varying(255) DEFAULT ''::character varying,
    expires_time timestamp without time zone NOT NULL,
    redirect_uri character varying(255) DEFAULT NULL::character varying,
    state character varying(255) DEFAULT ''::character varying NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_code IS 'OAuth2 授权码表';


--
-- Name: COLUMN system_oauth2_code.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.id IS '编号';


--
-- Name: COLUMN system_oauth2_code.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.user_id IS '用户编号';


--
-- Name: COLUMN system_oauth2_code.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.user_type IS '用户类型';


--
-- Name: COLUMN system_oauth2_code.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.code IS '授权码';


--
-- Name: COLUMN system_oauth2_code.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_code.scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.scopes IS '授权范围';


--
-- Name: COLUMN system_oauth2_code.expires_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.expires_time IS '过期时间';


--
-- Name: COLUMN system_oauth2_code.redirect_uri; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.redirect_uri IS '可重定向的 URI 地址';


--
-- Name: COLUMN system_oauth2_code.state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.state IS '状态';


--
-- Name: COLUMN system_oauth2_code.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_code.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_code.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_code.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_code.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.deleted IS '是否删除';


--
-- Name: COLUMN system_oauth2_code.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_code.tenant_id IS '租户编号';


--
-- Name: system_oauth2_code_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_code_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_oauth2_refresh_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_oauth2_refresh_token (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    refresh_token character varying(32) NOT NULL,
    user_type smallint NOT NULL,
    client_id character varying(255) NOT NULL,
    scopes character varying(255) DEFAULT NULL::character varying,
    expires_time timestamp without time zone NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_oauth2_refresh_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_oauth2_refresh_token IS 'OAuth2 刷新令牌';


--
-- Name: COLUMN system_oauth2_refresh_token.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.id IS '编号';


--
-- Name: COLUMN system_oauth2_refresh_token.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.user_id IS '用户编号';


--
-- Name: COLUMN system_oauth2_refresh_token.refresh_token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.refresh_token IS '刷新令牌';


--
-- Name: COLUMN system_oauth2_refresh_token.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.user_type IS '用户类型';


--
-- Name: COLUMN system_oauth2_refresh_token.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.client_id IS '客户端编号';


--
-- Name: COLUMN system_oauth2_refresh_token.scopes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.scopes IS '授权范围';


--
-- Name: COLUMN system_oauth2_refresh_token.expires_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.expires_time IS '过期时间';


--
-- Name: COLUMN system_oauth2_refresh_token.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.creator IS '创建者';


--
-- Name: COLUMN system_oauth2_refresh_token.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.create_time IS '创建时间';


--
-- Name: COLUMN system_oauth2_refresh_token.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.updater IS '更新者';


--
-- Name: COLUMN system_oauth2_refresh_token.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.update_time IS '更新时间';


--
-- Name: COLUMN system_oauth2_refresh_token.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.deleted IS '是否删除';


--
-- Name: COLUMN system_oauth2_refresh_token.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_oauth2_refresh_token.tenant_id IS '租户编号';


--
-- Name: system_oauth2_refresh_token_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_oauth2_refresh_token_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_operate_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_operate_log (
    id bigint NOT NULL,
    trace_id character varying(64) DEFAULT ''::character varying NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    type character varying(50) NOT NULL,
    sub_type character varying(50) NOT NULL,
    biz_id bigint NOT NULL,
    action character varying(2000) DEFAULT ''::character varying NOT NULL,
    extra character varying(2000) DEFAULT ''::character varying NOT NULL,
    request_method character varying(16) DEFAULT ''::character varying,
    request_url character varying(255) DEFAULT ''::character varying,
    user_ip character varying(50) DEFAULT NULL::character varying,
    user_agent character varying(200) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_operate_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_operate_log IS '操作日志记录 V2 版本';


--
-- Name: COLUMN system_operate_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.id IS '日志主键';


--
-- Name: COLUMN system_operate_log.trace_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.trace_id IS '链路追踪编号';


--
-- Name: COLUMN system_operate_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.user_id IS '用户编号';


--
-- Name: COLUMN system_operate_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.user_type IS '用户类型';


--
-- Name: COLUMN system_operate_log.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.type IS '操作模块类型';


--
-- Name: COLUMN system_operate_log.sub_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.sub_type IS '操作名';


--
-- Name: COLUMN system_operate_log.biz_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.biz_id IS '操作数据模块编号';


--
-- Name: COLUMN system_operate_log.action; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.action IS '操作内容';


--
-- Name: COLUMN system_operate_log.extra; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.extra IS '拓展字段';


--
-- Name: COLUMN system_operate_log.request_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.request_method IS '请求方法名';


--
-- Name: COLUMN system_operate_log.request_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.request_url IS '请求地址';


--
-- Name: COLUMN system_operate_log.user_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.user_ip IS '用户 IP';


--
-- Name: COLUMN system_operate_log.user_agent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.user_agent IS '浏览器 UA';


--
-- Name: COLUMN system_operate_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.creator IS '创建者';


--
-- Name: COLUMN system_operate_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.create_time IS '创建时间';


--
-- Name: COLUMN system_operate_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.updater IS '更新者';


--
-- Name: COLUMN system_operate_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.update_time IS '更新时间';


--
-- Name: COLUMN system_operate_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.deleted IS '是否删除';


--
-- Name: COLUMN system_operate_log.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_operate_log.tenant_id IS '租户编号';


--
-- Name: system_operate_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_operate_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_post; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_post (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(50) NOT NULL,
    sort integer NOT NULL,
    status smallint NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_post; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_post IS '岗位信息表';


--
-- Name: COLUMN system_post.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.id IS '岗位ID';


--
-- Name: COLUMN system_post.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.code IS '岗位编码';


--
-- Name: COLUMN system_post.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.name IS '岗位名称';


--
-- Name: COLUMN system_post.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.sort IS '显示顺序';


--
-- Name: COLUMN system_post.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.status IS '状态（0正常 1停用）';


--
-- Name: COLUMN system_post.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.remark IS '备注';


--
-- Name: COLUMN system_post.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.creator IS '创建者';


--
-- Name: COLUMN system_post.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.create_time IS '创建时间';


--
-- Name: COLUMN system_post.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.updater IS '更新者';


--
-- Name: COLUMN system_post.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.update_time IS '更新时间';


--
-- Name: COLUMN system_post.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.deleted IS '是否删除';


--
-- Name: COLUMN system_post.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_post.tenant_id IS '租户编号';


--
-- Name: system_post_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_post_seq
    START WITH 6
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_role (
    id bigint NOT NULL,
    name character varying(30) NOT NULL,
    code character varying(100) NOT NULL,
    sort integer NOT NULL,
    data_scope smallint DEFAULT 1 NOT NULL,
    data_scope_dept_ids character varying(500) DEFAULT ''::character varying NOT NULL,
    status smallint NOT NULL,
    type smallint NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_role IS '角色信息表';


--
-- Name: COLUMN system_role.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.id IS '角色ID';


--
-- Name: COLUMN system_role.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.name IS '角色名称';


--
-- Name: COLUMN system_role.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.code IS '角色权限字符串';


--
-- Name: COLUMN system_role.sort; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.sort IS '显示顺序';


--
-- Name: COLUMN system_role.data_scope; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.data_scope IS '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）';


--
-- Name: COLUMN system_role.data_scope_dept_ids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.data_scope_dept_ids IS '数据范围(指定部门数组)';


--
-- Name: COLUMN system_role.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.status IS '角色状态（0正常 1停用）';


--
-- Name: COLUMN system_role.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.type IS '角色类型';


--
-- Name: COLUMN system_role.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.remark IS '备注';


--
-- Name: COLUMN system_role.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.creator IS '创建者';


--
-- Name: COLUMN system_role.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.create_time IS '创建时间';


--
-- Name: COLUMN system_role.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.updater IS '更新者';


--
-- Name: COLUMN system_role.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.update_time IS '更新时间';


--
-- Name: COLUMN system_role.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.deleted IS '是否删除';


--
-- Name: COLUMN system_role.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role.tenant_id IS '租户编号';


--
-- Name: system_role_menu; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_role_menu (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_role_menu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_role_menu IS '角色和菜单关联表';


--
-- Name: COLUMN system_role_menu.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.id IS '自增编号';


--
-- Name: COLUMN system_role_menu.role_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.role_id IS '角色ID';


--
-- Name: COLUMN system_role_menu.menu_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.menu_id IS '菜单ID';


--
-- Name: COLUMN system_role_menu.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.creator IS '创建者';


--
-- Name: COLUMN system_role_menu.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.create_time IS '创建时间';


--
-- Name: COLUMN system_role_menu.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.updater IS '更新者';


--
-- Name: COLUMN system_role_menu.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.update_time IS '更新时间';


--
-- Name: COLUMN system_role_menu.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.deleted IS '是否删除';


--
-- Name: COLUMN system_role_menu.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_role_menu.tenant_id IS '租户编号';


--
-- Name: system_role_menu_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_role_menu_seq
    START WITH 5779
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_role_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_role_seq
    START WITH 112
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_sms_channel; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_sms_channel (
    id bigint NOT NULL,
    signature character varying(12) NOT NULL,
    code character varying(63) NOT NULL,
    status smallint NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    api_key character varying(128) NOT NULL,
    api_secret character varying(128) DEFAULT NULL::character varying,
    callback_url character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_sms_channel; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_sms_channel IS '短信渠道';


--
-- Name: COLUMN system_sms_channel.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.id IS '编号';


--
-- Name: COLUMN system_sms_channel.signature; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.signature IS '短信签名';


--
-- Name: COLUMN system_sms_channel.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.code IS '渠道编码';


--
-- Name: COLUMN system_sms_channel.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.status IS '开启状态';


--
-- Name: COLUMN system_sms_channel.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.remark IS '备注';


--
-- Name: COLUMN system_sms_channel.api_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.api_key IS '短信 API 的账号';


--
-- Name: COLUMN system_sms_channel.api_secret; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.api_secret IS '短信 API 的秘钥';


--
-- Name: COLUMN system_sms_channel.callback_url; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.callback_url IS '短信发送回调 URL';


--
-- Name: COLUMN system_sms_channel.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.creator IS '创建者';


--
-- Name: COLUMN system_sms_channel.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.create_time IS '创建时间';


--
-- Name: COLUMN system_sms_channel.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.updater IS '更新者';


--
-- Name: COLUMN system_sms_channel.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.update_time IS '更新时间';


--
-- Name: COLUMN system_sms_channel.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_channel.deleted IS '是否删除';


--
-- Name: system_sms_channel_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_sms_channel_seq
    START WITH 7
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_sms_code; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_sms_code (
    id bigint NOT NULL,
    mobile character varying(11) NOT NULL,
    code character varying(6) NOT NULL,
    create_ip character varying(15) NOT NULL,
    scene smallint NOT NULL,
    today_index smallint NOT NULL,
    used smallint NOT NULL,
    used_time timestamp without time zone,
    used_ip character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_sms_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_sms_code IS '手机验证码';


--
-- Name: COLUMN system_sms_code.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.id IS '编号';


--
-- Name: COLUMN system_sms_code.mobile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.mobile IS '手机号';


--
-- Name: COLUMN system_sms_code.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.code IS '验证码';


--
-- Name: COLUMN system_sms_code.create_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.create_ip IS '创建 IP';


--
-- Name: COLUMN system_sms_code.scene; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.scene IS '发送场景';


--
-- Name: COLUMN system_sms_code.today_index; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.today_index IS '今日发送的第几条';


--
-- Name: COLUMN system_sms_code.used; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.used IS '是否使用';


--
-- Name: COLUMN system_sms_code.used_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.used_time IS '使用时间';


--
-- Name: COLUMN system_sms_code.used_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.used_ip IS '使用 IP';


--
-- Name: COLUMN system_sms_code.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.creator IS '创建者';


--
-- Name: COLUMN system_sms_code.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.create_time IS '创建时间';


--
-- Name: COLUMN system_sms_code.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.updater IS '更新者';


--
-- Name: COLUMN system_sms_code.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.update_time IS '更新时间';


--
-- Name: COLUMN system_sms_code.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.deleted IS '是否删除';


--
-- Name: COLUMN system_sms_code.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_code.tenant_id IS '租户编号';


--
-- Name: system_sms_code_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_sms_code_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_sms_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_sms_log (
    id bigint NOT NULL,
    channel_id bigint NOT NULL,
    channel_code character varying(63) NOT NULL,
    template_id bigint NOT NULL,
    template_code character varying(63) NOT NULL,
    template_type smallint NOT NULL,
    template_content character varying(255) NOT NULL,
    template_params character varying(255) NOT NULL,
    api_template_id character varying(63) NOT NULL,
    mobile character varying(11) NOT NULL,
    user_id bigint,
    user_type smallint,
    send_status smallint DEFAULT 0 NOT NULL,
    send_time timestamp without time zone,
    api_send_code character varying(63) DEFAULT NULL::character varying,
    api_send_msg character varying(255) DEFAULT NULL::character varying,
    api_request_id character varying(255) DEFAULT NULL::character varying,
    api_serial_no character varying(255) DEFAULT NULL::character varying,
    receive_status smallint DEFAULT 0 NOT NULL,
    receive_time timestamp without time zone,
    api_receive_code character varying(63) DEFAULT NULL::character varying,
    api_receive_msg character varying(255) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_sms_log; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_sms_log IS '短信日志';


--
-- Name: COLUMN system_sms_log.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.id IS '编号';


--
-- Name: COLUMN system_sms_log.channel_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.channel_id IS '短信渠道编号';


--
-- Name: COLUMN system_sms_log.channel_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.channel_code IS '短信渠道编码';


--
-- Name: COLUMN system_sms_log.template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_id IS '模板编号';


--
-- Name: COLUMN system_sms_log.template_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_code IS '模板编码';


--
-- Name: COLUMN system_sms_log.template_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_type IS '短信类型';


--
-- Name: COLUMN system_sms_log.template_content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_content IS '短信内容';


--
-- Name: COLUMN system_sms_log.template_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.template_params IS '短信参数';


--
-- Name: COLUMN system_sms_log.api_template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_template_id IS '短信 API 的模板编号';


--
-- Name: COLUMN system_sms_log.mobile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.mobile IS '手机号';


--
-- Name: COLUMN system_sms_log.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.user_id IS '用户编号';


--
-- Name: COLUMN system_sms_log.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.user_type IS '用户类型';


--
-- Name: COLUMN system_sms_log.send_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.send_status IS '发送状态';


--
-- Name: COLUMN system_sms_log.send_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.send_time IS '发送时间';


--
-- Name: COLUMN system_sms_log.api_send_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_send_code IS '短信 API 发送结果的编码';


--
-- Name: COLUMN system_sms_log.api_send_msg; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_send_msg IS '短信 API 发送失败的提示';


--
-- Name: COLUMN system_sms_log.api_request_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_request_id IS '短信 API 发送返回的唯一请求 ID';


--
-- Name: COLUMN system_sms_log.api_serial_no; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_serial_no IS '短信 API 发送返回的序号';


--
-- Name: COLUMN system_sms_log.receive_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.receive_status IS '接收状态';


--
-- Name: COLUMN system_sms_log.receive_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.receive_time IS '接收时间';


--
-- Name: COLUMN system_sms_log.api_receive_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_receive_code IS 'API 接收结果的编码';


--
-- Name: COLUMN system_sms_log.api_receive_msg; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.api_receive_msg IS 'API 接收结果的说明';


--
-- Name: COLUMN system_sms_log.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.creator IS '创建者';


--
-- Name: COLUMN system_sms_log.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.create_time IS '创建时间';


--
-- Name: COLUMN system_sms_log.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.updater IS '更新者';


--
-- Name: COLUMN system_sms_log.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.update_time IS '更新时间';


--
-- Name: COLUMN system_sms_log.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_log.deleted IS '是否删除';


--
-- Name: system_sms_log_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_sms_log_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_sms_template; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_sms_template (
    id bigint NOT NULL,
    type smallint NOT NULL,
    status smallint NOT NULL,
    code character varying(63) NOT NULL,
    name character varying(63) NOT NULL,
    content character varying(255) NOT NULL,
    params character varying(255) NOT NULL,
    remark character varying(255) DEFAULT NULL::character varying,
    api_template_id character varying(63) NOT NULL,
    channel_id bigint NOT NULL,
    channel_code character varying(63) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_sms_template; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_sms_template IS '短信模板';


--
-- Name: COLUMN system_sms_template.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.id IS '编号';


--
-- Name: COLUMN system_sms_template.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.type IS '模板类型';


--
-- Name: COLUMN system_sms_template.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.status IS '开启状态';


--
-- Name: COLUMN system_sms_template.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.code IS '模板编码';


--
-- Name: COLUMN system_sms_template.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.name IS '模板名称';


--
-- Name: COLUMN system_sms_template.content; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.content IS '模板内容';


--
-- Name: COLUMN system_sms_template.params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.params IS '参数数组';


--
-- Name: COLUMN system_sms_template.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.remark IS '备注';


--
-- Name: COLUMN system_sms_template.api_template_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.api_template_id IS '短信 API 的模板编号';


--
-- Name: COLUMN system_sms_template.channel_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.channel_id IS '短信渠道编号';


--
-- Name: COLUMN system_sms_template.channel_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.channel_code IS '短信渠道编码';


--
-- Name: COLUMN system_sms_template.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.creator IS '创建者';


--
-- Name: COLUMN system_sms_template.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.create_time IS '创建时间';


--
-- Name: COLUMN system_sms_template.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.updater IS '更新者';


--
-- Name: COLUMN system_sms_template.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.update_time IS '更新时间';


--
-- Name: COLUMN system_sms_template.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_sms_template.deleted IS '是否删除';


--
-- Name: system_sms_template_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_sms_template_seq
    START WITH 17
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_social_client; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_social_client (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    social_type smallint NOT NULL,
    user_type smallint NOT NULL,
    client_id character varying(255) NOT NULL,
    client_secret character varying(255) NOT NULL,
    agent_id character varying(255) DEFAULT NULL::character varying,
    status smallint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_social_client; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_social_client IS '社交客户端表';


--
-- Name: COLUMN system_social_client.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.id IS '编号';


--
-- Name: COLUMN system_social_client.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.name IS '应用名';


--
-- Name: COLUMN system_social_client.social_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.social_type IS '社交平台的类型';


--
-- Name: COLUMN system_social_client.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.user_type IS '用户类型';


--
-- Name: COLUMN system_social_client.client_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.client_id IS '客户端编号';


--
-- Name: COLUMN system_social_client.client_secret; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.client_secret IS '客户端密钥';


--
-- Name: COLUMN system_social_client.agent_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.agent_id IS '代理编号';


--
-- Name: COLUMN system_social_client.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.status IS '状态';


--
-- Name: COLUMN system_social_client.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.creator IS '创建者';


--
-- Name: COLUMN system_social_client.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.create_time IS '创建时间';


--
-- Name: COLUMN system_social_client.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.updater IS '更新者';


--
-- Name: COLUMN system_social_client.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.update_time IS '更新时间';


--
-- Name: COLUMN system_social_client.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.deleted IS '是否删除';


--
-- Name: COLUMN system_social_client.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_client.tenant_id IS '租户编号';


--
-- Name: system_social_client_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_social_client_seq
    START WITH 44
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_social_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_social_user (
    id bigint NOT NULL,
    type smallint NOT NULL,
    openid character varying(32) NOT NULL,
    token character varying(256) DEFAULT NULL::character varying,
    raw_token_info character varying(1024) NOT NULL,
    nickname character varying(32) NOT NULL,
    avatar character varying(255) DEFAULT NULL::character varying,
    raw_user_info character varying(1024) NOT NULL,
    code character varying(256) NOT NULL,
    state character varying(256) DEFAULT NULL::character varying,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_social_user; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_social_user IS '社交用户表';


--
-- Name: COLUMN system_social_user.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.id IS '主键(自增策略)';


--
-- Name: COLUMN system_social_user.type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.type IS '社交平台的类型';


--
-- Name: COLUMN system_social_user.openid; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.openid IS '社交 openid';


--
-- Name: COLUMN system_social_user.token; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.token IS '社交 token';


--
-- Name: COLUMN system_social_user.raw_token_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.raw_token_info IS '原始 Token 数据，一般是 JSON 格式';


--
-- Name: COLUMN system_social_user.nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.nickname IS '用户昵称';


--
-- Name: COLUMN system_social_user.avatar; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.avatar IS '用户头像';


--
-- Name: COLUMN system_social_user.raw_user_info; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.raw_user_info IS '原始用户数据，一般是 JSON 格式';


--
-- Name: COLUMN system_social_user.code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.code IS '最后一次的认证 code';


--
-- Name: COLUMN system_social_user.state; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.state IS '最后一次的认证 state';


--
-- Name: COLUMN system_social_user.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.creator IS '创建者';


--
-- Name: COLUMN system_social_user.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.create_time IS '创建时间';


--
-- Name: COLUMN system_social_user.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.updater IS '更新者';


--
-- Name: COLUMN system_social_user.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.update_time IS '更新时间';


--
-- Name: COLUMN system_social_user.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.deleted IS '是否删除';


--
-- Name: COLUMN system_social_user.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user.tenant_id IS '租户编号';


--
-- Name: system_social_user_bind; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_social_user_bind (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    user_type smallint NOT NULL,
    social_type smallint NOT NULL,
    social_user_id bigint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_social_user_bind; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_social_user_bind IS '社交绑定表';


--
-- Name: COLUMN system_social_user_bind.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.id IS '主键(自增策略)';


--
-- Name: COLUMN system_social_user_bind.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.user_id IS '用户编号';


--
-- Name: COLUMN system_social_user_bind.user_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.user_type IS '用户类型';


--
-- Name: COLUMN system_social_user_bind.social_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.social_type IS '社交平台的类型';


--
-- Name: COLUMN system_social_user_bind.social_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.social_user_id IS '社交用户的编号';


--
-- Name: COLUMN system_social_user_bind.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.creator IS '创建者';


--
-- Name: COLUMN system_social_user_bind.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.create_time IS '创建时间';


--
-- Name: COLUMN system_social_user_bind.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.updater IS '更新者';


--
-- Name: COLUMN system_social_user_bind.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.update_time IS '更新时间';


--
-- Name: COLUMN system_social_user_bind.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.deleted IS '是否删除';


--
-- Name: COLUMN system_social_user_bind.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_social_user_bind.tenant_id IS '租户编号';


--
-- Name: system_social_user_bind_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_social_user_bind_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_social_user_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_social_user_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_tenant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_tenant (
    id bigint NOT NULL,
    name character varying(30) NOT NULL,
    contact_user_id bigint,
    contact_name character varying(30) NOT NULL,
    contact_mobile character varying(500) DEFAULT NULL::character varying,
    status smallint DEFAULT 0 NOT NULL,
    website character varying(256) DEFAULT ''::character varying,
    package_id bigint NOT NULL,
    expire_time timestamp without time zone NOT NULL,
    account_count integer NOT NULL,
    creator character varying(64) DEFAULT ''::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_tenant; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_tenant IS '租户表';


--
-- Name: COLUMN system_tenant.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.id IS '租户编号';


--
-- Name: COLUMN system_tenant.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.name IS '租户名';


--
-- Name: COLUMN system_tenant.contact_user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.contact_user_id IS '联系人的用户编号';


--
-- Name: COLUMN system_tenant.contact_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.contact_name IS '联系人';


--
-- Name: COLUMN system_tenant.contact_mobile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.contact_mobile IS '联系手机';


--
-- Name: COLUMN system_tenant.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.status IS '租户状态（0正常 1停用）';


--
-- Name: COLUMN system_tenant.website; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.website IS '绑定域名';


--
-- Name: COLUMN system_tenant.package_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.package_id IS '租户套餐编号';


--
-- Name: COLUMN system_tenant.expire_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.expire_time IS '过期时间';


--
-- Name: COLUMN system_tenant.account_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.account_count IS '账号数量';


--
-- Name: COLUMN system_tenant.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.creator IS '创建者';


--
-- Name: COLUMN system_tenant.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.create_time IS '创建时间';


--
-- Name: COLUMN system_tenant.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.updater IS '更新者';


--
-- Name: COLUMN system_tenant.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.update_time IS '更新时间';


--
-- Name: COLUMN system_tenant.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant.deleted IS '是否删除';


--
-- Name: system_tenant_package; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_tenant_package (
    id bigint CONSTRAINT system_tenant_package_id_not_null1 NOT NULL,
    name character varying(30) NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(256) DEFAULT ''::character varying,
    menu_ids character varying(4096) NOT NULL,
    creator character varying(64) DEFAULT ''::character varying NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_tenant_package; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_tenant_package IS '租户套餐表';


--
-- Name: COLUMN system_tenant_package.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.id IS '套餐编号';


--
-- Name: COLUMN system_tenant_package.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.name IS '套餐名';


--
-- Name: COLUMN system_tenant_package.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.status IS '租户状态（0正常 1停用）';


--
-- Name: COLUMN system_tenant_package.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.remark IS '备注';


--
-- Name: COLUMN system_tenant_package.menu_ids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.menu_ids IS '关联的菜单编号';


--
-- Name: COLUMN system_tenant_package.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.creator IS '创建者';


--
-- Name: COLUMN system_tenant_package.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.create_time IS '创建时间';


--
-- Name: COLUMN system_tenant_package.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.updater IS '更新者';


--
-- Name: COLUMN system_tenant_package.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.update_time IS '更新时间';


--
-- Name: COLUMN system_tenant_package.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_tenant_package.deleted IS '是否删除';


--
-- Name: system_tenant_package_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_tenant_package_seq
    START WITH 112
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_tenant_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_tenant_seq
    START WITH 123
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_user_post; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_user_post (
    id bigint NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    post_id bigint DEFAULT 0 NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_user_post; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_user_post IS '用户岗位表';


--
-- Name: COLUMN system_user_post.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.id IS 'id';


--
-- Name: COLUMN system_user_post.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.user_id IS '用户ID';


--
-- Name: COLUMN system_user_post.post_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.post_id IS '岗位ID';


--
-- Name: COLUMN system_user_post.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.creator IS '创建者';


--
-- Name: COLUMN system_user_post.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.create_time IS '创建时间';


--
-- Name: COLUMN system_user_post.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.updater IS '更新者';


--
-- Name: COLUMN system_user_post.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.update_time IS '更新时间';


--
-- Name: COLUMN system_user_post.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.deleted IS '是否删除';


--
-- Name: COLUMN system_user_post.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_post.tenant_id IS '租户编号';


--
-- Name: system_user_post_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_user_post_seq
    START WITH 125
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_user_role (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL
);


--
-- Name: TABLE system_user_role; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_user_role IS '用户和角色关联表';


--
-- Name: COLUMN system_user_role.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.id IS '自增编号';


--
-- Name: COLUMN system_user_role.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.user_id IS '用户ID';


--
-- Name: COLUMN system_user_role.role_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.role_id IS '角色ID';


--
-- Name: COLUMN system_user_role.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.creator IS '创建者';


--
-- Name: COLUMN system_user_role.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.create_time IS '创建时间';


--
-- Name: COLUMN system_user_role.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.updater IS '更新者';


--
-- Name: COLUMN system_user_role.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.update_time IS '更新时间';


--
-- Name: COLUMN system_user_role.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.deleted IS '是否删除';


--
-- Name: COLUMN system_user_role.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_user_role.tenant_id IS '租户编号';


--
-- Name: system_user_role_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_user_role_seq
    START WITH 39
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_users (
    id bigint NOT NULL,
    username character varying(30) NOT NULL,
    password character varying(100) DEFAULT ''::character varying NOT NULL,
    nickname character varying(30) NOT NULL,
    remark character varying(500) DEFAULT NULL::character varying,
    dept_id bigint,
    post_ids character varying(255) DEFAULT NULL::character varying,
    email character varying(50) DEFAULT ''::character varying,
    mobile character varying(11) DEFAULT ''::character varying,
    sex smallint DEFAULT 0,
    avatar character varying(512) DEFAULT ''::character varying,
    status smallint DEFAULT 0 NOT NULL,
    login_ip character varying(50) DEFAULT ''::character varying,
    login_date timestamp without time zone,
    creator character varying(64) DEFAULT ''::character varying,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updater character varying(64) DEFAULT ''::character varying,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    tenant_id bigint DEFAULT 0 NOT NULL,
    integral bigint DEFAULT 0,
    do_experiment integer DEFAULT 0
);


--
-- Name: TABLE system_users; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.system_users IS '用户信息表';


--
-- Name: COLUMN system_users.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.id IS '用户ID';


--
-- Name: COLUMN system_users.username; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.username IS '用户账号';


--
-- Name: COLUMN system_users.password; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.password IS '密码';


--
-- Name: COLUMN system_users.nickname; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.nickname IS '用户昵称';


--
-- Name: COLUMN system_users.remark; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.remark IS '备注';


--
-- Name: COLUMN system_users.dept_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.dept_id IS '部门ID';


--
-- Name: COLUMN system_users.post_ids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.post_ids IS '岗位编号数组';


--
-- Name: COLUMN system_users.email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.email IS '用户邮箱';


--
-- Name: COLUMN system_users.mobile; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.mobile IS '手机号码';


--
-- Name: COLUMN system_users.sex; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.sex IS '用户性别';


--
-- Name: COLUMN system_users.avatar; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.avatar IS '头像地址';


--
-- Name: COLUMN system_users.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.status IS '帐号状态（0正常 1停用）';


--
-- Name: COLUMN system_users.login_ip; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.login_ip IS '最后登录IP';


--
-- Name: COLUMN system_users.login_date; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.login_date IS '最后登录时间';


--
-- Name: COLUMN system_users.creator; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.creator IS '创建者';


--
-- Name: COLUMN system_users.create_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.create_time IS '创建时间';


--
-- Name: COLUMN system_users.updater; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.updater IS '更新者';


--
-- Name: COLUMN system_users.update_time; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.update_time IS '更新时间';


--
-- Name: COLUMN system_users.deleted; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.deleted IS '是否删除';


--
-- Name: COLUMN system_users.tenant_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.tenant_id IS '租户编号';


--
-- Name: COLUMN system_users.integral; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.integral IS '用户积分';


--
-- Name: COLUMN system_users.do_experiment; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.system_users.do_experiment IS '是否在做实验：0-否，1-是';


--
-- Name: system_users_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_users_seq
    START WITH 132
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: warehouse_dataset_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.warehouse_dataset_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: warehouse_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.warehouse_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: yudao_demo01_contact_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.yudao_demo01_contact_seq
    START WITH 2
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: yudao_demo02_category_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.yudao_demo02_category_seq
    START WITH 7
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: yudao_demo03_course_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.yudao_demo03_course_seq
    START WITH 14
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: yudao_demo03_grade_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.yudao_demo03_grade_seq
    START WITH 10
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: yudao_demo03_student_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.yudao_demo03_student_seq
    START WITH 10
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Data for Name: dual; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.dual  FROM stdin;
\.


--
-- Data for Name: infra_api_access_log; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_api_access_log (id, trace_id, user_id, user_type, application_name, request_method, request_url, request_params, response_body, user_ip, user_agent, operate_module, operate_name, operate_type, begin_time, end_time, duration, result_code, result_msg, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: infra_api_error_log; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_api_error_log (id, trace_id, user_id, user_type, application_name, request_method, request_url, request_params, user_ip, user_agent, exception_time, exception_name, exception_message, exception_root_cause_message, exception_stack_trace, exception_class_name, exception_file_name, exception_method_name, exception_line_number, process_status, process_time, process_user_id, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: infra_codegen_column; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_codegen_column (id, table_id, column_name, data_type, column_comment, nullable, primary_key, ordinal_position, java_type, java_field, dict_type, example, create_operation, update_operation, list_operation, list_operation_condition, list_operation_result, html_type, creator, create_time, updater, update_time, deleted) FROM stdin;
503	30	mac_address	VARCHAR	MAC地址	t	f	16	String	macAddress		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.272	1	2024-07-10 17:47:44.272	1
504	30	active_status	SMALLINT	激活状态 0:未激活 1:已激活	t	f	17	Short	activeStatus		2	t	t	t	=	t	radio	1	2024-07-10 17:47:44.283	1	2024-07-10 17:47:44.283	1
505	30	extension	VARCHAR	扩展json	t	f	18	String	extension		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.294	1	2024-07-10 17:47:44.294	1
506	30	activated_time	TIMESTAMP	激活时间	t	f	19	LocalDateTime	activatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 17:47:44.304	1	2024-07-10 17:47:44.304	1
507	30	last_online_time	TIMESTAMP	最后上线时间	t	f	20	LocalDateTime	lastOnlineTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 17:47:44.314	1	2024-07-10 17:47:44.314	1
508	30	parent_did	VARCHAR	子设备关联网关的设备唯一标识	t	f	21	String	parentDid		26872	t	t	t	=	t	input	1	2024-07-10 17:47:44.335	1	2024-07-10 17:47:44.335	1
509	30	device_type	VARCHAR	支持以下两种产品类型\t•COMMON：普通产品，需直连设备。\t•GATEWAY：网关产品，可挂载子设备。\t•SUBSET：子设备。	t	f	22	String	deviceType		1	t	t	t	=	t	select	1	2024-07-10 17:47:44.35	1	2024-07-10 17:47:44.35	1
510	30	latitude	NUMERIC	纬度	t	f	23	BigDecimal	latitude		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.36	1	2024-07-10 17:47:44.36	1
511	30	longitude	NUMERIC	经度	t	f	24	BigDecimal	longitude		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.377	1	2024-07-10 17:47:44.377	1
512	30	location_name	VARCHAR	设备所在位置	t	f	25	String	locationName		李四	t	t	t	LIKE	t	input	1	2024-07-10 17:47:44.467	1	2024-07-10 17:47:44.467	1
513	30	province_code	VARCHAR	省,直辖市编码	t	f	26	String	provinceCode		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.579	1	2024-07-10 17:47:44.579	1
514	30	city_code	VARCHAR	市编码	t	f	27	String	cityCode		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.619	1	2024-07-10 17:47:44.619	1
515	30	region_code	VARCHAR	区县	t	f	28	String	regionCode		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.631	1	2024-07-10 17:47:44.631	1
516	30	tenant_id	BIGINT	租户ID	t	f	29	Long	tenantId		17327	f	f	f	=	f	input	1	2024-07-10 17:47:44.642	1	2024-07-10 17:47:44.642	1
517	30	product_name	VARCHAR	产品名称	t	f	30	String	productName		张三	t	t	t	LIKE	t	input	1	2024-07-10 17:47:44.652	1	2024-07-10 17:47:44.652	1
518	30	is_shadow	SMALLINT	是否启用设备影子(0=禁用，1=启用)	t	f	31	Short	isShadow		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.688	1	2024-07-10 17:47:44.688	1
519	30	things_model_value	OTHER	物模型值	t	f	32	Object	thingsModelValue		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.7	1	2024-07-10 17:47:44.7	1
520	30	product_type_id	BIGINT	产品类型ID	t	f	33	Long	productTypeId		1486	t	t	t	=	t	input	1	2024-07-10 17:47:44.714	1	2024-07-10 17:47:44.714	1
521	30	product_type_name	VARCHAR	产品类型名称	t	f	34	String	productTypeName		张三	t	t	t	LIKE	t	input	1	2024-07-10 17:47:44.728	1	2024-07-10 17:47:44.728	1
522	30	group_id	BIGINT	分组ID	t	f	35	Long	groupId		8334	t	t	t	=	t	input	1	2024-07-10 17:47:44.738	1	2024-07-10 17:47:44.738	1
602	34	file_url	VARCHAR	文件地址	t	f	3	String	fileUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2024-07-10 18:03:25.282	1	2024-07-10 18:42:41.382	0
603	34	upload_time	TIMESTAMP	上传时间	t	f	4	LocalDateTime	uploadTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:25.307	1	2024-07-10 18:42:41.391	0
604	34	file_name	VARCHAR	文件名称	t	f	5	String	fileName		张三	t	t	t	LIKE	t	input	1	2024-07-10 18:03:25.331	1	2024-07-10 18:42:41.403	0
605	34	file_size	BIGINT	文件大小	t	f	6	Long	fileSize		\N	t	t	t	=	t	input	1	2024-07-10 18:03:25.352	1	2024-07-10 18:42:41.412	0
606	34	remark	VARCHAR	备注	t	f	7	String	remark		你猜	t	t	t	=	t	input	1	2024-07-10 18:03:25.372	1	2024-07-10 18:42:41.421	0
15	2	id	BIGINT	id	f	t	1	Long	id		7457	f	t	f	=	t	input	1	2024-07-10 10:32:59.178	1	2024-07-10 10:32:59.178	1
16	2	did	VARCHAR	设备唯一标识	f	f	2	String	did		17217	t	t	t	=	t	input	1	2024-07-10 10:32:59.196	1	2024-07-10 10:32:59.196	1
17	2	name	VARCHAR	设备名称	t	f	3	String	name		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:32:59.209	1	2024-07-10 10:32:59.209	1
18	2	description	VARCHAR	设备描述	t	f	4	String	description		你说的对	t	t	t	=	t	editor	1	2024-07-10 10:32:59.219	1	2024-07-10 10:32:59.219	1
19	2	enabled_status	VARCHAR	设备状态： ENABLE:启用 || DISABLE:禁用	t	f	5	String	enabledStatus		2	t	t	t	=	t	radio	1	2024-07-10 10:32:59.308	1	2024-07-10 10:32:59.308	1
20	2	connect_status	VARCHAR	连接状态 :    OFFLINE:离线 || ONLINE:在线	t	f	6	String	connectStatus		1	t	t	t	=	t	radio	1	2024-07-10 10:32:59.412	1	2024-07-10 10:32:59.412	1
21	2	pid	VARCHAR	产品唯一标识	f	f	7	String	pid		6790	t	t	t	=	t	input	1	2024-07-10 10:32:59.433	1	2024-07-10 10:32:59.433	1
22	2	create_by	VARCHAR	创建者	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.473	1	2024-07-10 10:32:59.473	1
23	2	create_time	TIMESTAMP	创建时间	t	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:32:59.518	1	2024-07-10 10:32:59.518	1
24	2	update_by	VARCHAR	更新者	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.586	1	2024-07-10 10:32:59.586	1
25	2	update_time	TIMESTAMP	更新时间	t	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:32:59.597	1	2024-07-10 10:32:59.597	1
26	2	remark	VARCHAR	备注	t	f	12	String	remark		随便	t	t	t	=	t	input	1	2024-07-10 10:32:59.606	1	2024-07-10 10:32:59.606	1
27	2	device_version	VARCHAR	设备版本号	t	f	13	String	deviceVersion		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.621	1	2024-07-10 10:32:59.621	1
28	2	device_sn	VARCHAR	设备sn号	f	f	14	String	deviceSn		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.638	1	2024-07-10 10:32:59.638	1
50	3	id	BIGINT	设备ID	f	t	1	Long	id		19517	f	t	f	=	t	input	1	2024-07-10 10:33:02.256	1	2024-07-10 10:33:02.256	1
51	3	group_name	VARCHAR	分组ID	f	f	2	String	groupName		李四	t	t	t	LIKE	t	input	1	2024-07-10 10:33:02.289	1	2024-07-10 10:33:02.289	1
52	3	create_by	VARCHAR	创建者	t	f	3	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:02.298	1	2024-07-10 10:33:02.298	1
53	3	create_time	TIMESTAMP	创建时间	t	f	4	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:02.337	1	2024-07-10 10:33:02.337	1
54	3	update_by	VARCHAR	更新者	t	f	5	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:02.38	1	2024-07-10 10:33:02.38	1
55	3	update_time	TIMESTAMP	更新时间	t	f	6	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:02.467	1	2024-07-10 10:33:02.467	1
56	3	tenant_id	BIGINT	租户ID	t	f	7	Long	tenantId		19567	f	f	f	=	f	input	1	2024-07-10 10:33:02.648	1	2024-07-10 10:33:02.648	1
97	7	id	BIGINT	主键	f	t	1	Long	id		19986	f	t	f	=	t	input	1	2024-07-10 10:33:09.022	1	2024-07-10 10:33:09.022	1
81	6	id	BIGINT	主键	f	t	1	Long	id		18516	f	t	f	=	t	input	1	2024-07-10 10:33:07.402	1	2024-07-10 10:33:07.402	1
82	6	app_id	VARCHAR	应用ID	f	f	2	String	appId		25182	t	t	t	=	t	input	1	2024-07-10 10:33:07.411	1	2024-07-10 10:33:07.411	1
83	6	package_name	VARCHAR	包名称	f	f	3	String	packageName		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:33:07.421	1	2024-07-10 10:33:07.421	1
70	5	id	BIGINT	id	f	t	1	Long	id		13975	f	t	f	=	t	input	1	2024-07-10 10:33:06.071	1	2024-07-10 10:33:06.071	1
71	5	did	VARCHAR	设备标识	f	f	2	String	did		7136	t	t	t	=	t	input	1	2024-07-10 10:33:06.102	1	2024-07-10 10:33:06.102	1
72	5	type	VARCHAR	类型(0:基础Topic,1:自定义Topic)	t	f	3	String	type		2	t	t	t	=	t	select	1	2024-07-10 10:33:06.131	1	2024-07-10 10:33:06.131	1
73	5	topic	VARCHAR	topic	t	f	4	String	topic		\N	t	t	t	=	t	input	1	2024-07-10 10:33:06.148	1	2024-07-10 10:33:06.148	1
74	5	publisher	VARCHAR	发布者	t	f	5	String	publisher		\N	t	t	t	=	t	input	1	2024-07-10 10:33:06.159	1	2024-07-10 10:33:06.159	1
75	5	subscriber	VARCHAR	订阅者	t	f	6	String	subscriber		\N	t	t	t	=	t	input	1	2024-07-10 10:33:06.174	1	2024-07-10 10:33:06.174	1
76	5	create_by	VARCHAR	创建者	t	f	7	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:06.183	1	2024-07-10 10:33:06.183	1
77	5	create_time	TIMESTAMP	创建时间	f	f	8	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:06.202	1	2024-07-10 10:33:06.202	1
78	5	update_by	VARCHAR	更新者	t	f	9	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:06.225	1	2024-07-10 10:33:06.225	1
79	5	update_time	TIMESTAMP	更新时间	f	f	10	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:06.239	1	2024-07-10 10:33:06.239	1
80	5	remark	VARCHAR	备注	t	f	11	String	remark		你猜	t	t	t	=	t	input	1	2024-07-10 10:33:06.281	1	2024-07-10 10:33:06.281	1
57	4	id	BIGINT	id	f	t	1	Long	id		4942	f	t	f	=	t	input	1	2024-07-10 10:33:03.572	1	2024-07-10 10:33:03.572	1
58	4	did	VARCHAR	设备唯一标识	t	f	2	String	did		3362	t	t	t	=	t	input	1	2024-07-10 10:33:03.779	1	2024-07-10 10:33:03.779	1
59	4	file_url	VARCHAR	文件地址	t	f	3	String	fileUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2024-07-10 10:33:03.795	1	2024-07-10 10:33:03.795	1
60	4	upload_time	TIMESTAMP	上传时间	t	f	4	LocalDateTime	uploadTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:03.803	1	2024-07-10 10:33:03.803	1
61	4	file_name	VARCHAR	文件名称	t	f	5	String	fileName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 10:33:03.82	1	2024-07-10 10:33:03.82	1
642	37	did	VARCHAR	设备标识	f	f	3	String	did		11199	t	t	t	=	t	input	1	2024-07-10 18:03:28.699	1	2024-07-10 18:42:13.374	0
643	37	upgrade_status	SMALLINT	升级状态(0:待升级、1:升级中、2:升级成功、3:升级失败)	f	f	4	Short	upgradeStatus		1	t	t	t	=	t	radio	1	2024-07-10 18:03:28.71	1	2024-07-10 18:42:13.39	0
644	37	progress	SMALLINT	升级进度（百分比）	f	f	5	Short	progress		\N	t	t	t	=	t	input	1	2024-07-10 18:03:28.721	1	2024-07-10 18:42:13.4	0
572	32	ip_address	VARCHAR	IP地址	t	f	15	String	ipAddress		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.078	1	2024-07-10 18:42:58.864	0
573	32	mac_address	VARCHAR	MAC地址	t	f	16	String	macAddress		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.096	1	2024-07-10 18:42:58.874	0
574	32	active_status	SMALLINT	激活状态 0:未激活 1:已激活	t	f	17	Short	activeStatus		1	t	t	t	=	t	radio	1	2024-07-10 18:03:22.107	1	2024-07-10 18:42:58.883	0
575	32	extension	VARCHAR	扩展json	t	f	18	String	extension		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.119	1	2024-07-10 18:42:58.894	0
576	32	activated_time	TIMESTAMP	激活时间	t	f	19	LocalDateTime	activatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:22.133	1	2024-07-10 18:42:58.903	0
577	32	last_online_time	TIMESTAMP	最后上线时间	t	f	20	LocalDateTime	lastOnlineTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:22.142	1	2024-07-10 18:42:58.914	0
578	32	parent_did	VARCHAR	子设备关联网关的设备唯一标识	t	f	21	String	parentDid		14118	t	t	t	=	t	input	1	2024-07-10 18:03:22.152	1	2024-07-10 18:42:58.935	0
587	32	product_name	VARCHAR	产品名称	t	f	30	String	productName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 18:03:22.293	1	2024-07-10 18:42:59.187	0
588	32	is_shadow	SMALLINT	是否启用设备影子(0=禁用，1=启用)	t	f	31	Short	isShadow		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.311	1	2024-07-10 18:42:59.199	0
589	32	things_model_value	OTHER	物模型值	t	f	32	Object	thingsModelValue		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.322	1	2024-07-10 18:42:59.208	0
590	32	product_type_id	BIGINT	产品类型ID	t	f	33	Long	productTypeId		15153	t	t	t	=	t	input	1	2024-07-10 18:03:22.345	1	2024-07-10 18:42:59.221	0
591	32	product_type_name	VARCHAR	产品类型名称	t	f	34	String	productTypeName		李四	t	t	t	LIKE	t	input	1	2024-07-10 18:03:22.357	1	2024-07-10 18:42:59.231	0
592	32	group_id	BIGINT	分组ID	t	f	35	Long	groupId		4406	t	t	t	=	t	input	1	2024-07-10 18:03:22.376	1	2024-07-10 18:42:59.24	0
593	33	id	BIGINT	设备ID	f	t	1	Long	id		20688	f	t	f	=	t	input	1	2024-07-10 18:03:24.434	1	2024-07-10 18:42:49.904	0
693	39	product_type_id	BIGINT	产品类型ID	t	f	24	Long	productTypeId		22912	t	t	t	=	t	input	1	2024-07-10 18:03:31.089	1	2024-07-10 18:41:58.48	0
115	8	id	BIGINT	主键	f	t	1	Long	id		30509	f	t	f	=	t	input	1	2024-07-10 10:33:10.898	1	2024-07-10 10:33:10.898	1
116	8	upgrade_id	BIGINT	升级包ID，关联ota_upgrades表	f	f	2	Long	upgradeId		19197	t	t	t	=	t	input	1	2024-07-10 10:33:10.946	1	2024-07-10 10:33:10.946	1
117	8	task_name	VARCHAR	任务名称	f	f	3	String	taskName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 10:33:10.971	1	2024-07-10 10:33:10.971	1
118	8	task_status	SMALLINT	任务状态(0:待发布、1:进行中、2:已完成、3:已取消)	f	f	4	Short	taskStatus		1	t	t	t	=	t	radio	1	2024-07-10 10:33:11.044	1	2024-07-10 10:33:11.044	1
119	8	scheduled_time	TIMESTAMP	计划执行时间	t	f	5	LocalDateTime	scheduledTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:11.077	1	2024-07-10 10:33:11.077	1
120	8	description	VARCHAR	任务描述	t	f	6	String	description		你说的对	t	t	t	=	t	editor	1	2024-07-10 10:33:11.09	1	2024-07-10 10:33:11.09	1
121	8	remark	VARCHAR	描述	t	f	7	String	remark		随便	t	t	t	=	t	input	1	2024-07-10 10:33:11.176	1	2024-07-10 10:33:11.176	1
122	8	created_by	BIGINT	创建人	t	f	8	Long	createdBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:11.31	1	2024-07-10 10:33:11.31	1
123	8	created_time	TIMESTAMP	创建时间	f	f	9	LocalDateTime	createdTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:11.357	1	2024-07-10 10:33:11.357	1
124	8	updated_by	BIGINT	更新人	t	f	10	Long	updatedBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:11.424	1	2024-07-10 10:33:11.424	1
125	8	updated_time	TIMESTAMP	更新时间	f	f	11	LocalDateTime	updatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:11.46	1	2024-07-10 10:33:11.46	1
126	8	tenant_id	BIGINT	租户ID	t	f	12	Long	tenantId		27654	f	f	f	=	f	input	1	2024-07-10 10:33:11.569	1	2024-07-10 10:33:11.569	1
100	7	upgrade_status	SMALLINT	升级状态(0:待升级、1:升级中、2:升级成功、3:升级失败)	f	f	4	Short	upgradeStatus		1	t	t	t	=	t	radio	1	2024-07-10 10:33:09.059	1	2024-07-10 10:33:09.059	1
101	7	progress	SMALLINT	升级进度（百分比）	f	f	5	Short	progress		\N	t	t	t	=	t	input	1	2024-07-10 10:33:09.103	1	2024-07-10 10:33:09.103	1
102	7	error_code	VARCHAR	错误代码	t	f	6	String	errorCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:09.113	1	2024-07-10 10:33:09.113	1
103	7	error_message	VARCHAR	错误信息	t	f	7	String	errorMessage		\N	t	t	t	=	t	input	1	2024-07-10 10:33:09.132	1	2024-07-10 10:33:09.132	1
104	7	start_time	TIMESTAMP	升级开始时间	t	f	8	LocalDateTime	startTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:09.143	1	2024-07-10 10:33:09.143	1
105	7	end_time	TIMESTAMP	升级结束时间	t	f	9	LocalDateTime	endTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:09.16	1	2024-07-10 10:33:09.16	1
106	7	success_details	VARCHAR	升级成功详细信息	t	f	10	String	successDetails		\N	t	t	t	=	t	input	1	2024-07-10 10:33:09.172	1	2024-07-10 10:33:09.172	1
107	7	failure_details	VARCHAR	升级失败详细信息	t	f	11	String	failureDetails		\N	t	t	t	=	t	input	1	2024-07-10 10:33:09.192	1	2024-07-10 10:33:09.192	1
760	44	maxlength	VARCHAR	指示字符串长度。\t仅当dataType为string时生效。	t	f	7	String	maxlength		\N	t	t	t	=	t	input	1	2024-07-10 18:03:36.623	1	2024-07-10 18:41:15.91	0
724	42	commands_id	BIGINT	命令ID	f	f	2	Long	commandsId		26237	t	t	t	=	t	input	1	2024-07-10 18:03:34.254	1	2024-07-10 18:41:33.104	0
725	42	service_id	BIGINT	服务ID	t	f	3	Long	serviceId		14687	t	t	t	=	t	input	1	2024-07-10 18:03:34.263	1	2024-07-10 18:41:33.222	0
706	41	service_id	BIGINT	服务ID	f	f	2	Long	serviceId		23008	t	t	t	=	t	input	1	2024-07-10 18:03:32.907	1	2024-07-10 18:41:41.686	0
707	41	commands_id	BIGINT	命令ID	f	f	3	Long	commandsId		10861	t	t	t	=	t	input	1	2024-07-10 18:03:32.923	1	2024-07-10 18:41:41.699	0
694	39	product_type_name	VARCHAR	产品类型名称	t	f	25	String	productTypeName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 18:03:31.106	1	2024-07-10 18:41:58.489	0
695	39	manufacturer_code	VARCHAR	厂商Code:支持英文大小写，数字，下划线和中划线	f	f	26	String	manufacturerCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:31.119	1	2024-07-10 18:41:58.499	0
600	34	id	BIGINT	id	f	t	1	Long	id		32159	f	t	f	=	t	input	1	2024-07-10 18:03:25.248	1	2024-07-10 18:42:41.353	0
594	33	group_name	VARCHAR	分组ID	f	f	2	String	groupName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 18:03:24.478	1	2024-07-10 18:42:49.913	0
595	33	create_by	VARCHAR	创建者	t	f	3	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:24.525	1	2024-07-10 18:42:49.922	0
596	33	create_time	TIMESTAMP	创建时间	t	f	4	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:24.541	1	2024-07-10 18:42:49.976	0
597	33	update_by	VARCHAR	更新者	t	f	5	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:24.57	1	2024-07-10 18:42:50.172	0
162	11	id	BIGINT	id	f	t	1	Long	id		24305	f	t	f	=	t	input	1	2024-07-10 10:33:17.003	1	2024-07-10 10:33:17.003	1
163	11	service_id	BIGINT	服务ID	f	f	2	Long	serviceId		7796	t	t	t	=	t	input	1	2024-07-10 10:33:17.016	1	2024-07-10 10:33:17.016	1
164	11	commands_id	BIGINT	命令ID	f	f	3	Long	commandsId		28070	t	t	t	=	t	input	1	2024-07-10 10:33:17.029	1	2024-07-10 10:33:17.029	1
165	11	datatype	VARCHAR	指示数据类型。取值范围：string、int、decimal\t	f	f	4	String	datatype		1	t	t	t	=	t	select	1	2024-07-10 10:33:17.045	1	2024-07-10 10:33:17.045	1
166	11	enumlist	VARCHAR	指示枚举值。\t如开关状态status可有如下取值\t"enumList" : ["OPEN","CLOSE"]\t目前本字段是非功能性字段，仅起到描述作用。建议准确定义。\t	t	f	5	String	enumlist		\N	t	t	t	=	t	input	1	2024-07-10 10:33:17.059	1	2024-07-10 10:33:17.059	1
167	11	max	VARCHAR	指示最大值。\t仅当dataType为int、decimal时生效，逻辑小于等于。	t	f	6	String	max		\N	t	t	t	=	t	input	1	2024-07-10 10:33:17.089	1	2024-07-10 10:33:17.089	1
168	11	maxlength	VARCHAR	指示字符串长度。\t仅当dataType为string时生效。	t	f	7	String	maxlength		\N	t	t	t	=	t	input	1	2024-07-10 10:33:17.099	1	2024-07-10 10:33:17.099	1
169	11	min	VARCHAR	指示最小值。\t仅当dataType为int、decimal时生效，逻辑大于等于。	t	f	8	String	min		\N	t	t	t	=	t	input	1	2024-07-10 10:33:17.11	1	2024-07-10 10:33:17.11	1
170	11	parameter_description	VARCHAR	命令中参数的描述，不影响实际功能，可配置为空字符串""。	t	f	9	String	parameterDescription		你说的对	t	t	t	=	t	editor	1	2024-07-10 10:33:17.131	1	2024-07-10 10:33:17.131	1
153	10	id	BIGINT	命令id	f	t	1	Long	id		8307	f	t	f	=	t	input	1	2024-07-10 10:33:14.988	1	2024-07-10 10:33:14.988	1
154	10	service_id	BIGINT	服务ID	f	f	2	Long	serviceId		22067	t	t	t	=	t	input	1	2024-07-10 10:33:14.998	1	2024-07-10 10:33:14.998	1
155	10	name	VARCHAR	指示命令的名字，如门磁的LOCK命令、摄像头的VIDEO_RECORD命令，命令名与参数共同构成一个完整的命令。\t支持英文大小写、数字及下划线，长度[2,50]。\t	f	f	3	String	name		李四	t	t	t	LIKE	t	input	1	2024-07-10 10:33:15.041	1	2024-07-10 10:33:15.041	1
156	10	description	VARCHAR	命令描述。	t	f	4	String	description		随便	t	t	t	=	t	editor	1	2024-07-10 10:33:15.064	1	2024-07-10 10:33:15.064	1
157	10	create_by	VARCHAR	创建者	t	f	5	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:15.096	1	2024-07-10 10:33:15.096	1
158	10	create_time	TIMESTAMP	创建时间	f	f	6	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:15.109	1	2024-07-10 10:33:15.109	1
159	10	update_by	VARCHAR	更新者	t	f	7	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:15.134	1	2024-07-10 10:33:15.134	1
160	10	update_time	TIMESTAMP	更新时间	f	f	8	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:15.148	1	2024-07-10 10:33:15.148	1
161	10	tenant_id	BIGINT	租户ID	t	f	9	Long	tenantId		5307	f	f	f	=	f	input	1	2024-07-10 10:33:15.168	1	2024-07-10 10:33:15.168	1
147	9	encrypt_key	VARCHAR	加密密钥	t	f	21	String	encryptKey		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.645	1	2024-07-10 10:33:13.645	1
148	9	encrypt_vector	VARCHAR	加密向量	t	f	22	String	encryptVector		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.663	1	2024-07-10 10:33:13.663	1
149	9	tenant_id	BIGINT	租户ID	t	f	23	Long	tenantId		21730	f	f	f	=	f	input	1	2024-07-10 10:33:13.68	1	2024-07-10 10:33:13.68	1
150	9	product_type_id	BIGINT	产品类型ID	t	f	24	Long	productTypeId		29799	t	t	t	=	t	input	1	2024-07-10 10:33:13.697	1	2024-07-10 10:33:13.697	1
151	9	product_type_name	VARCHAR	产品类型名称	t	f	25	String	productTypeName		张三	t	t	t	LIKE	t	input	1	2024-07-10 10:33:13.708	1	2024-07-10 10:33:13.708	1
152	9	manufacturer_code	VARCHAR	厂商Code:支持英文大小写，数字，下划线和中划线	f	f	26	String	manufacturerCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.721	1	2024-07-10 10:33:13.721	1
761	44	min	VARCHAR	指示最小值。\t仅当dataType为int、decimal时生效，逻辑大于等于。	t	f	8	String	min		\N	t	t	t	=	t	input	1	2024-07-10 18:03:36.637	1	2024-07-10 18:41:15.924	0
615	35	type	VARCHAR	类型(0:基础Topic,1:自定义Topic)	t	f	3	String	type		1	t	t	t	=	t	select	1	2024-07-10 18:03:26.378	1	2024-07-10 18:42:31.77	0
616	35	topic	VARCHAR	topic	t	f	4	String	topic		\N	t	t	t	=	t	input	1	2024-07-10 18:03:26.389	1	2024-07-10 18:42:31.785	0
617	35	publisher	VARCHAR	发布者	t	f	5	String	publisher		\N	t	t	t	=	t	input	1	2024-07-10 18:03:26.399	1	2024-07-10 18:42:31.796	0
618	35	subscriber	VARCHAR	订阅者	t	f	6	String	subscriber		\N	t	t	t	=	t	input	1	2024-07-10 18:03:26.481	1	2024-07-10 18:42:31.807	0
619	35	create_by	VARCHAR	创建者	t	f	7	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:26.512	1	2024-07-10 18:42:31.822	0
620	35	create_time	TIMESTAMP	创建时间	f	f	8	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:26.571	1	2024-07-10 18:42:31.833	0
621	35	update_by	VARCHAR	更新者	t	f	9	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:26.624	1	2024-07-10 18:42:31.845	0
622	35	update_time	TIMESTAMP	更新时间	f	f	10	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:26.638	1	2024-07-10 18:42:31.882	0
623	35	remark	VARCHAR	备注	t	f	11	String	remark		你猜	t	t	t	=	t	input	1	2024-07-10 18:03:26.661	1	2024-07-10 18:42:31.895	0
638	36	updated_time	TIMESTAMP	更新时间	f	f	15	LocalDateTime	updatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:27.686	1	2024-07-10 18:42:25.183	0
639	36	tenant_id	BIGINT	租户ID	t	f	16	Long	tenantId		32592	f	f	f	=	f	input	1	2024-07-10 18:03:27.695	1	2024-07-10 18:42:25.2	0
795	46	template_code	VARCHAR	产品模版标识	t	f	4	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:38.445	1	2024-07-10 18:40:55.807	0
229	15	id	BIGINT	id	f	t	1	Long	id		26934	f	t	f	=	t	input	1	2024-07-10 10:33:23.596	1	2024-07-10 10:33:23.596	1
211	14	id	BIGINT	id	f	t	1	Long	id		26706	f	t	f	=	t	input	1	2024-07-10 10:33:22.176	1	2024-07-10 10:33:22.176	1
212	14	event_id	BIGINT	事件id	f	f	2	Long	eventId		19391	t	t	t	=	t	input	1	2024-07-10 10:33:22.214	1	2024-07-10 10:33:22.214	1
213	14	service_id	BIGINT	服务ID	t	f	3	Long	serviceId		7451	t	t	t	=	t	input	1	2024-07-10 10:33:22.231	1	2024-07-10 10:33:22.231	1
198	13	id	BIGINT	id	f	t	1	Long	id		18852	f	t	f	=	t	input	1	2024-07-10 10:33:20.079	1	2024-07-10 10:33:20.079	1
199	13	event_name	VARCHAR	事件名称	f	f	2	String	eventName		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:33:20.101	1	2024-07-10 10:33:20.101	1
200	13	event_code	VARCHAR	事件code	f	f	3	String	eventCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:20.111	1	2024-07-10 10:33:20.111	1
201	13	event_type	VARCHAR	事件类型。\tINFO_EVENT_TYPE：信息。\tALERT_EVENT_TYPE：告警。\tERROR_EVENT_TYPE：故障	f	f	4	String	eventType		1	t	t	t	=	t	select	1	2024-07-10 10:33:20.151	1	2024-07-10 10:33:20.151	1
202	13	template_code	VARCHAR	模板code	t	f	5	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:20.214	1	2024-07-10 10:33:20.214	1
203	13	pid	VARCHAR	产品唯一标识	t	f	6	String	pid		3944	t	t	t	=	t	input	1	2024-07-10 10:33:20.284	1	2024-07-10 10:33:20.284	1
204	13	enabled_status	VARCHAR	状态(字典值：0启用  1停用)	t	f	7	String	enabledStatus		2	t	t	t	=	t	radio	1	2024-07-10 10:33:20.417	1	2024-07-10 10:33:20.417	1
205	13	description	VARCHAR	描述	t	f	8	String	description		你说的对	t	t	t	=	t	editor	1	2024-07-10 10:33:20.496	1	2024-07-10 10:33:20.496	1
206	13	create_by	VARCHAR	创建者	t	f	9	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:20.627	1	2024-07-10 10:33:20.627	1
207	13	create_time	TIMESTAMP	创建时间	t	f	10	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:20.675	1	2024-07-10 10:33:20.675	1
208	13	update_by	VARCHAR	更新者	t	f	11	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:20.684	1	2024-07-10 10:33:20.684	1
209	13	update_time	TIMESTAMP	更新时间	t	f	12	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:20.695	1	2024-07-10 10:33:20.695	1
210	13	tenant_id	BIGINT	租户ID	t	f	13	Long	tenantId		14508	f	f	f	=	f	input	1	2024-07-10 10:33:20.716	1	2024-07-10 10:33:20.716	1
189	12	parameter_name	VARCHAR	命令中参数的名字。	t	f	10	String	parameterName		张三	t	t	t	LIKE	t	input	1	2024-07-10 10:33:18.868	1	2024-07-10 10:33:18.868	1
190	12	required	VARCHAR	指示本条属性是否必填，取值为0或1，默认取值1（必填）。\t目前本字段是非功能性字段，仅起到描述作用。	f	f	11	String	required		\N	t	t	t	=	t	input	1	2024-07-10 10:33:18.88	1	2024-07-10 10:33:18.88	1
191	12	step	VARCHAR	指示步长。	t	f	12	String	step		\N	t	t	t	=	t	input	1	2024-07-10 10:33:18.899	1	2024-07-10 10:33:18.899	1
192	12	unit	VARCHAR	指示单位。\t取值根据参数确定，如：\t•温度单位：“C”或“K”\t•百分比单位：“%”\t•压强单位：“Pa”或“kPa”\t	t	f	13	String	unit		\N	t	t	t	=	t	input	1	2024-07-10 10:33:18.942	1	2024-07-10 10:33:18.942	1
193	12	create_by	VARCHAR	创建者	t	f	14	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:18.955	1	2024-07-10 10:33:18.955	1
194	12	create_time	TIMESTAMP	创建时间	f	f	15	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:18.973	1	2024-07-10 10:33:18.973	1
195	12	update_by	VARCHAR	更新者	t	f	16	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:18.982	1	2024-07-10 10:33:18.982	1
796	46	pid	VARCHAR	产品标识	t	f	5	String	pid		17856	t	t	t	=	t	input	1	2024-07-10 18:03:38.51	1	2024-07-10 18:40:55.82	0
797	46	status	VARCHAR	状态(字典值：0启用  1停用)	f	f	6	String	status		1	t	t	t	=	t	radio	1	2024-07-10 18:03:38.585	1	2024-07-10 18:40:55.831	0
799	46	create_by	VARCHAR	创建者	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:38.609	1	2024-07-10 18:40:55.878	0
800	46	create_time	TIMESTAMP	创建时间	f	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:38.626	1	2024-07-10 18:40:55.889	0
801	46	update_by	VARCHAR	更新者	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:38.641	1	2024-07-10 18:40:55.907	0
802	46	update_time	TIMESTAMP	更新时间	f	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:38.65	1	2024-07-10 18:40:55.928	0
803	46	tenant_id	BIGINT	租户ID	t	f	12	Long	tenantId		17685	f	f	f	=	f	input	1	2024-07-10 18:03:38.659	1	2024-07-10 18:40:55.944	0
777	45	enumlist	VARCHAR	指示枚举值:如开关状态status可有如下取值"enumList" : ["OPEN","CLOSE"]目前本字段是非功能性字段，仅起到描述作用。建议准确定义。	t	f	6	String	enumlist		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.606	1	2024-07-10 18:41:06.427	0
640	37	id	BIGINT	主键	f	t	1	Long	id		28064	f	t	f	=	t	input	1	2024-07-10 18:03:28.661	1	2024-07-10 18:42:13.347	0
641	37	task_id	BIGINT	任务ID，关联ota_upgrade_tasks表	f	f	2	Long	taskId		12466	t	t	t	=	t	input	1	2024-07-10 18:03:28.687	1	2024-07-10 18:42:13.359	0
637	36	updated_by	BIGINT	更新人	t	f	14	Long	updatedBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:27.671	1	2024-07-10 18:42:25.169	0
877	52	tenant_id	BIGINT	租户ID	t	f	11	Long	tenantId		27732	f	f	f	=	f	input	1	2024-07-10 18:03:45.015	1	2024-07-10 18:40:10.21	0
261	17	id	BIGINT	id	f	t	1	Long	id		17029	f	t	f	=	t	input	1	2024-07-10 10:33:26.763	1	2024-07-10 10:33:26.763	1
262	17	app_id	VARCHAR	应用ID	f	f	2	String	appId		11797	t	t	t	=	t	input	1	2024-07-10 10:33:26.789	1	2024-07-10 10:33:26.789	1
249	16	id	BIGINT	服务id	f	t	1	Long	id		20066	f	t	f	=	t	input	1	2024-07-10 10:33:25.097	1	2024-07-10 10:33:25.097	1
250	16	service_code	VARCHAR	服务编码:支持英文大小写、数字、下划线和中划线	f	f	2	String	serviceCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:25.108	1	2024-07-10 10:33:25.108	1
251	16	service_name	VARCHAR	服务名称	f	f	3	String	serviceName		李四	t	t	t	LIKE	t	input	1	2024-07-10 10:33:25.12	1	2024-07-10 10:33:25.12	1
252	16	template_code	VARCHAR	产品模版标识	t	f	4	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:25.15	1	2024-07-10 10:33:25.15	1
253	16	pid	VARCHAR	产品标识	t	f	5	String	pid		16556	t	t	t	=	t	input	1	2024-07-10 10:33:25.179	1	2024-07-10 10:33:25.179	1
254	16	status	VARCHAR	状态(字典值：0启用  1停用)	f	f	6	String	status		1	t	t	t	=	t	radio	1	2024-07-10 10:33:25.196	1	2024-07-10 10:33:25.196	1
255	16	description	VARCHAR	服务的描述信息:文本描述，不影响实际功能，可配置为空字符串""。\t	t	f	7	String	description		随便	t	t	t	=	t	editor	1	2024-07-10 10:33:25.206	1	2024-07-10 10:33:25.206	1
256	16	create_by	VARCHAR	创建者	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:25.223	1	2024-07-10 10:33:25.223	1
257	16	create_time	TIMESTAMP	创建时间	f	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:25.281	1	2024-07-10 10:33:25.281	1
258	16	update_by	VARCHAR	更新者	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:25.304	1	2024-07-10 10:33:25.304	1
232	15	datatype	VARCHAR	指示数据类型：取值范围：string、int、decimal（float和double都可以使用此类型）、DateTime、jsonObject上报数据时，复杂类型数据格式如下：\t•DateTime:yyyyMMdd’T’HHmmss’Z’如:20151212T121212Z•jsonObject：自定义json结构体，平台不理解只透传	f	f	4	String	datatype		1	t	t	t	=	t	select	1	2024-07-10 10:33:23.89	1	2024-07-10 10:33:23.89	1
233	15	description	VARCHAR	属性描述，不影响实际功能，可配置为空字符串""。	t	f	5	String	description		你说的对	t	t	t	=	t	editor	1	2024-07-10 10:33:23.993	1	2024-07-10 10:33:23.993	1
234	15	enumlist	VARCHAR	指示枚举值:如开关状态status可有如下取值"enumList" : ["OPEN","CLOSE"]目前本字段是非功能性字段，仅起到描述作用。建议准确定义。	t	f	6	String	enumlist		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.006	1	2024-07-10 10:33:24.006	1
235	15	max	VARCHAR	指示最大值。支持长度不超过50的数字。仅当dataType为int、decimal时生效，逻辑小于等于。	t	f	7	String	max		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.048	1	2024-07-10 10:33:24.048	1
236	15	maxlength	BIGINT	指示字符串长度。仅当dataType为string、DateTime时生效。	t	f	8	Long	maxlength		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.059	1	2024-07-10 10:33:24.059	1
237	15	method	VARCHAR	指示访问模式。R:可读；W:可写；E属性值更改时上报数据取值范围：R、RW、RE、RWE	t	f	9	String	method		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.071	1	2024-07-10 10:33:24.071	1
238	15	min	VARCHAR	指示最小值。支持长度不超过50的数字。仅当dataType为int、decimal时生效，逻辑大于等于。	t	f	10	String	min		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.084	1	2024-07-10 10:33:24.084	1
240	15	step	INTEGER	指示步长。	t	f	12	Integer	step		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.113	1	2024-07-10 10:33:24.113	1
660	38	task_name	VARCHAR	任务名称	f	f	3	String	taskName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 18:03:29.545	1	2024-07-10 18:42:05.97	0
661	38	task_status	SMALLINT	任务状态(0:待发布、1:进行中、2:已完成、3:已取消)	f	f	4	Short	taskStatus		1	t	t	t	=	t	radio	1	2024-07-10 18:03:29.573	1	2024-07-10 18:42:05.993	0
662	38	scheduled_time	TIMESTAMP	计划执行时间	t	f	5	LocalDateTime	scheduledTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:29.593	1	2024-07-10 18:42:06.016	0
663	38	description	VARCHAR	任务描述	t	f	6	String	description		你说的对	t	t	t	=	t	editor	1	2024-07-10 18:03:29.614	1	2024-07-10 18:42:06.04	0
664	38	remark	VARCHAR	描述	t	f	7	String	remark		你猜	t	t	t	=	t	input	1	2024-07-10 18:03:29.629	1	2024-07-10 18:42:06.053	0
665	38	created_by	BIGINT	创建人	t	f	8	Long	createdBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:29.666	1	2024-07-10 18:42:06.062	0
666	38	created_time	TIMESTAMP	创建时间	f	f	9	LocalDateTime	createdTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:29.702	1	2024-07-10 18:42:06.073	0
667	38	updated_by	BIGINT	更新人	t	f	10	Long	updatedBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:29.72	1	2024-07-10 18:42:06.087	0
668	38	updated_time	TIMESTAMP	更新时间	f	f	11	LocalDateTime	updatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:29.731	1	2024-07-10 18:42:06.097	0
669	38	tenant_id	BIGINT	租户ID	t	f	12	Long	tenantId		16812	f	f	f	=	f	input	1	2024-07-10 18:03:29.751	1	2024-07-10 18:42:06.113	0
776	45	description	VARCHAR	属性描述，不影响实际功能，可配置为空字符串""。	t	f	5	String	description		你猜	t	t	t	=	t	editor	1	2024-07-10 18:03:37.596	1	2024-07-10 18:41:06.414	0
759	44	max	VARCHAR	指示最大值。\t仅当dataType为int、decimal时生效，逻辑小于等于。	t	f	6	String	max		\N	t	t	t	=	t	input	1	2024-07-10 18:03:36.614	1	2024-07-10 18:41:15.9	0
762	44	parameter_description	VARCHAR	命令中参数的描述，不影响实际功能，可配置为空字符串""。	t	f	9	String	parameterDescription		随便	t	t	t	=	t	editor	1	2024-07-10 18:03:36.657	1	2024-07-10 18:41:16.012	0
763	44	parameter_name	VARCHAR	命令中参数的名字。	t	f	10	String	parameterName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 18:03:36.684	1	2024-07-10 18:41:16.099	0
764	44	required	VARCHAR	指示本条属性是否必填，取值为0或1，默认取值1（必填）。\t目前本字段是非功能性字段，仅起到描述作用。	f	f	11	String	required		\N	t	t	t	=	t	input	1	2024-07-10 18:03:36.695	1	2024-07-10 18:41:16.11	0
329	21	id	BIGINT	主键	f	t	1	Long	id		7441	f	t	f	=	t	input	1	2024-07-10 10:33:33.383	1	2024-07-10 10:33:33.383	1
331	21	app_id	VARCHAR	应用ID	f	f	2	String	appId		11787	t	t	t	=	t	input	1	2024-07-10 10:33:33.47	1	2024-07-10 10:33:33.47	1
333	21	rule_code	VARCHAR	规则标识	f	f	3	String	ruleCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:33.525	1	2024-07-10 10:33:33.525	1
335	21	rule_name	VARCHAR	规则名称	f	f	4	String	ruleName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 10:33:33.589	1	2024-07-10 10:33:33.589	1
337	21	job_code	VARCHAR	任务标识	f	f	5	String	jobCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:33.601	1	2024-07-10 10:33:33.601	1
339	21	status	VARCHAR	状态(字典值：0启用  1停用)	f	f	6	String	status		2	t	t	t	=	t	radio	1	2024-07-10 10:33:33.618	1	2024-07-10 10:33:33.618	1
281	19	id	BIGINT	id	f	t	1	Long	id		22090	f	t	f	=	t	input	1	2024-07-10 10:33:29.482	1	2024-07-10 10:33:29.482	1
282	19	app_id	VARCHAR	应用ID	f	f	2	String	appId		3540	t	t	t	=	t	input	1	2024-07-10 10:33:29.554	1	2024-07-10 10:33:29.554	1
283	19	pid	VARCHAR	产品标识	f	f	3	String	pid		30177	t	t	t	=	t	input	1	2024-07-10 10:33:29.783	1	2024-07-10 10:33:29.783	1
284	19	protocol_name	VARCHAR	协议名称	t	f	4	String	protocolName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 10:33:29.795	1	2024-07-10 10:33:29.795	1
285	19	protocol_code	VARCHAR	协议标识	t	f	5	String	protocolCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:29.811	1	2024-07-10 10:33:29.811	1
286	19	protocol_version	VARCHAR	协议版本	t	f	6	String	protocolVersion		\N	t	t	t	=	t	input	1	2024-07-10 10:33:29.827	1	2024-07-10 10:33:29.827	1
287	19	protocol_type	VARCHAR	协议类型 ：mqtt || coap || modbus || http	t	f	7	String	protocolType		1	t	t	t	=	t	select	1	2024-07-10 10:33:29.848	1	2024-07-10 10:33:29.848	1
288	19	protocol_voice	VARCHAR	协议语言	t	f	8	String	protocolVoice		\N	t	t	t	=	t	input	1	2024-07-10 10:33:29.899	1	2024-07-10 10:33:29.899	1
289	19	class_name	VARCHAR	类名	t	f	9	String	className		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:33:29.91	1	2024-07-10 10:33:29.91	1
290	19	file_path	VARCHAR	文件地址	t	f	10	String	filePath		\N	t	t	t	=	t	input	1	2024-07-10 10:33:29.948	1	2024-07-10 10:33:29.948	1
291	19	content	VARCHAR	内容	t	f	11	String	content		\N	t	t	t	=	t	editor	1	2024-07-10 10:33:30.008	1	2024-07-10 10:33:30.008	1
292	19	status	VARCHAR	状态(字典值：0启用  1停用)	f	f	12	String	status		2	t	t	t	=	t	radio	1	2024-07-10 10:33:30.111	1	2024-07-10 10:33:30.111	1
293	19	create_by	VARCHAR	创建者	t	f	13	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:30.165	1	2024-07-10 10:33:30.165	1
294	19	create_time	TIMESTAMP	创建时间	f	f	14	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:30.207	1	2024-07-10 10:33:30.207	1
275	18	parent_id	VARCHAR	父级ID	t	f	4	String	parentId		31891	t	t	t	=	t	input	1	2024-07-10 10:33:28.114	1	2024-07-10 10:33:28.114	1
276	18	create_by	VARCHAR	创建者	t	f	5	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:28.138	1	2024-07-10 10:33:28.138	1
277	18	create_time	TIMESTAMP	创建时间	t	f	6	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:28.151	1	2024-07-10 10:33:28.151	1
278	18	update_by	VARCHAR	更新者	t	f	7	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:28.168	1	2024-07-10 10:33:28.168	1
279	18	update_time	TIMESTAMP	更新时间	t	f	8	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:28.21	1	2024-07-10 10:33:28.21	1
280	18	tenant_id	BIGINT	租户ID	t	f	9	Long	tenantId		31972	f	f	f	=	f	input	1	2024-07-10 10:33:28.308	1	2024-07-10 10:33:28.308	1
766	44	unit	VARCHAR	指示单位。\t取值根据参数确定，如：\t•温度单位：“C”或“K”\t•百分比单位：“%”\t•压强单位：“Pa”或“kPa”	t	f	13	String	unit		\N	t	t	t	=	t	input	1	2024-07-10 18:03:36.717	1	2024-07-10 18:41:16.144	0
767	44	create_by	VARCHAR	创建者	t	f	14	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:36.726	1	2024-07-10 18:41:16.154	0
768	44	create_time	TIMESTAMP	创建时间	t	f	15	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:36.801	1	2024-07-10 18:41:16.164	0
769	44	update_by	VARCHAR	更新者	t	f	16	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:36.925	1	2024-07-10 18:41:16.179	0
682	39	update_by	VARCHAR	更新者	t	f	13	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:30.768	1	2024-07-10 18:41:58.35	0
683	39	update_time	TIMESTAMP	更新时间	t	f	14	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:30.835	1	2024-07-10 18:41:58.36	0
347	22	id	BIGINT	规则告警ID	f	t	1	Long	id		13623	f	t	f	=	t	input	1	2024-07-10 10:33:35.213	1	2024-07-10 10:33:35.213	1
348	22	rule_id	BIGINT	规则ID	t	f	2	Long	ruleId		15919	t	t	t	=	t	input	1	2024-07-10 10:33:35.224	1	2024-07-10 10:33:35.224	1
557	31	group_id	BIGINT	分组ID	t	f	35	Long	groupId		20237	t	t	t	=	t	input	1	2024-07-10 17:52:40.348	1	2024-07-10 17:56:36.912	1
549	31	city_code	VARCHAR	市编码	t	f	27	String	cityCode		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.226	1	2024-07-10 17:56:36.693	1
550	31	region_code	VARCHAR	区县	t	f	28	String	regionCode		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.244	1	2024-07-10 17:56:36.705	1
551	31	tenant_id	BIGINT	租户ID	t	f	29	Long	tenantId		24555	f	f	f	=	f	input	1	2024-07-10 17:52:40.261	1	2024-07-10 17:56:36.716	1
378	25	id	BIGINT	主键	f	t	1	Long	id		29955	f	t	f	=	t	input	1	2024-07-10 10:33:36.659	1	2024-07-10 10:33:36.659	1
380	25	alarm_time	TIMESTAMP	告警时间	t	f	2	LocalDateTime	alarmTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:36.761	1	2024-07-10 10:33:36.761	1
382	25	alarm_name	VARCHAR	告警名称	t	f	3	String	alarmName		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:33:36.777	1	2024-07-10 10:33:36.777	1
384	25	alarm_level	INTEGER	告警级别	t	f	4	Integer	alarmLevel		\N	t	t	t	=	t	input	1	2024-07-10 10:33:36.792	1	2024-07-10 10:33:36.792	1
425	27	id	BIGINT	主键	f	t	1	Long	id		8281	f	t	f	=	t	input	1	2024-07-10 10:33:39.006	1	2024-07-10 10:33:39.006	1
426	27	rule_id	BIGINT	规则ID	f	f	2	Long	ruleId		15131	t	t	t	=	t	input	1	2024-07-10 10:33:39.027	1	2024-07-10 10:33:39.027	1
429	27	pid	VARCHAR	产品标识	t	f	5	String	pid		24713	t	t	t	=	t	input	1	2024-07-10 10:33:39.224	1	2024-07-10 10:33:39.224	1
386	25	alarm_describe	VARCHAR	告警描述	t	f	5	String	alarmDescribe		\N	t	t	t	=	t	input	1	2024-07-10 10:33:36.853	1	2024-07-10 10:33:36.853	1
389	25	processing_result	INTEGER	处理结果 0 未处理 1已处理	t	f	6	Integer	processingResult		\N	t	t	t	=	t	input	1	2024-07-10 10:33:36.871	1	2024-07-10 10:33:36.871	1
392	25	processing_opinions	VARCHAR	处理意见	t	f	7	String	processingOpinions		\N	t	t	t	=	t	input	1	2024-07-10 10:33:36.975	1	2024-07-10 10:33:36.975	1
395	25	alarm_content	VARCHAR	告警内容	t	f	8	String	alarmContent		\N	t	t	t	=	t	editor	1	2024-07-10 10:33:36.997	1	2024-07-10 10:33:36.997	1
398	25	processing_people	VARCHAR	处理人	t	f	9	String	processingPeople		\N	t	t	t	=	t	input	1	2024-07-10 10:33:37.024	1	2024-07-10 10:33:37.024	1
404	25	tenant_id	BIGINT	租户ID	t	f	11	Long	tenantId		31278	f	f	f	=	f	input	1	2024-07-10 10:33:37.127	1	2024-07-10 10:33:37.127	1
349	22	rule_alarm_name	VARCHAR	告警规则名称	t	f	3	String	ruleAlarmName		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:33:35.295	1	2024-07-10 10:33:35.295	1
350	22	rule_alarm_status	INTEGER	告警状态0 未启动  1运行中	t	f	4	Integer	ruleAlarmStatus		1	t	t	t	=	t	radio	1	2024-07-10 10:33:35.316	1	2024-07-10 10:33:35.316	1
351	22	rule_alarm_remark	VARCHAR	告警规则描述	t	f	5	String	ruleAlarmRemark		随便	t	t	t	=	t	input	1	2024-07-10 10:33:35.339	1	2024-07-10 10:33:35.339	1
352	22	rule_level	INTEGER	告警级别	t	f	6	Integer	ruleLevel		\N	t	t	t	=	t	input	1	2024-07-10 10:33:35.416	1	2024-07-10 10:33:35.416	1
353	22	notice_type	INTEGER	通知方式	t	f	7	Integer	noticeType		2	t	t	t	=	t	select	1	2024-07-10 10:33:35.43	1	2024-07-10 10:33:35.43	1
354	22	create_by	VARCHAR	创建人	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:35.441	1	2024-07-10 10:33:35.441	1
355	22	create_time	TIMESTAMP	创建时间	f	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:35.464	1	2024-07-10 10:33:35.464	1
356	22	update_by	VARCHAR	更新人	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:35.631	1	2024-07-10 10:33:35.631	1
358	22	update_time	TIMESTAMP	更新时间	f	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:35.642	1	2024-07-10 10:33:35.642	1
360	22	tenant_id	BIGINT	租户ID	t	f	12	Long	tenantId		27087	f	f	f	=	f	input	1	2024-07-10 10:33:35.652	1	2024-07-10 10:33:35.652	1
340	21	triggering	SMALLINT	触发机制（0:全部，1:任意一个）	t	f	7	Short	triggering		\N	t	t	t	=	t	input	1	2024-07-10 10:33:33.635	1	2024-07-10 10:33:33.635	1
341	21	remark	VARCHAR	规则描述，可以为空	t	f	8	String	remark		你说的对	t	t	t	=	t	input	1	2024-07-10 10:33:33.649	1	2024-07-10 10:33:33.649	1
342	21	create_by	VARCHAR	创建人	t	f	9	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:33.675	1	2024-07-10 10:33:33.675	1
343	21	create_time	TIMESTAMP	创建时间	f	f	10	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:33.69	1	2024-07-10 10:33:33.69	1
344	21	update_by	VARCHAR	更新人	t	f	11	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:33.699	1	2024-07-10 10:33:33.699	1
345	21	update_time	TIMESTAMP	更新时间	f	f	12	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:33.71	1	2024-07-10 10:33:33.71	1
346	21	tenant_id	BIGINT	租户ID	t	f	13	Long	tenantId		18243	f	f	f	=	f	input	1	2024-07-10 10:33:33.795	1	2024-07-10 10:33:33.795	1
271	17	tenant_id	BIGINT	租户ID	t	f	11	Long	tenantId		8701	f	f	f	=	f	input	1	2024-07-10 10:33:27.043	1	2024-07-10 10:33:27.043	1
214	14	datatype	VARCHAR	指示数据类型。取值范围：string、int、decimal	f	f	4	String	datatype		2	t	t	t	=	t	select	1	2024-07-10 10:33:22.279	1	2024-07-10 10:33:22.279	1
215	14	enumlist	VARCHAR	指示枚举值。\t如开关状态status可有如下取值\t"enumList" : ["OPEN","CLOSE"]\t目前本字段是非功能性字段，仅起到描述作用。建议准确定义。	t	f	5	String	enumlist		\N	t	t	t	=	t	input	1	2024-07-10 10:33:22.315	1	2024-07-10 10:33:22.315	1
216	14	max	VARCHAR	指示最大值。\t仅当dataType为int、decimal时生效，逻辑小于等于。	t	f	6	String	max		\N	t	t	t	=	t	input	1	2024-07-10 10:33:22.372	1	2024-07-10 10:33:22.372	1
681	39	create_time	TIMESTAMP	创建时间	t	f	12	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:30.748	1	2024-07-10 18:41:58.341	0
704	40	tenant_id	BIGINT	租户ID	t	f	9	Long	tenantId		9668	f	f	f	=	f	input	1	2024-07-10 18:03:32.114	1	2024-07-10 18:41:48.84	0
726	42	datatype	VARCHAR	指示数据类型。取值范围：string、int、decimal\t	f	f	4	String	datatype		2	t	t	t	=	t	select	1	2024-07-10 18:03:34.282	1	2024-07-10 18:41:33.349	0
727	42	enumlist	VARCHAR	指示枚举值。\t如开关状态status可有如下取值\t"enumList" : ["OPEN","CLOSE"]\t目前本字段是非功能性字段，仅起到描述作用。建议准确定义。\t	t	f	5	String	enumlist		\N	t	t	t	=	t	input	1	2024-07-10 18:03:34.305	1	2024-07-10 18:41:33.358	0
705	41	id	BIGINT	id	f	t	1	Long	id		31163	f	t	f	=	t	input	1	2024-07-10 18:03:32.878	1	2024-07-10 18:41:41.671	0
684	39	auth_mode	VARCHAR	认证方式	t	f	15	String	authMode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:30.891	1	2024-07-10 18:41:58.375	0
685	39	user_name	VARCHAR	用户名	t	f	16	String	userName		王五	t	t	t	LIKE	t	input	1	2024-07-10 18:03:30.978	1	2024-07-10 18:41:58.386	0
686	39	password	VARCHAR	密码	t	f	17	String	password		\N	t	t	t	=	t	input	1	2024-07-10 18:03:30.989	1	2024-07-10 18:41:58.397	0
687	39	connector	VARCHAR	连接实例	t	f	18	String	connector		\N	t	t	t	=	t	input	1	2024-07-10 18:03:31.003	1	2024-07-10 18:41:58.407	0
688	39	sign_key	VARCHAR	签名密钥	t	f	19	String	signKey		\N	t	t	t	=	t	input	1	2024-07-10 18:03:31.03	1	2024-07-10 18:41:58.423	0
689	39	encrypt_method	INTEGER	协议加密方式 0：不加密 1：SM4加密 2：AES加密	t	f	20	Integer	encryptMethod		\N	t	t	t	=	t	input	1	2024-07-10 18:03:31.043	1	2024-07-10 18:41:58.439	0
690	39	encrypt_key	VARCHAR	加密密钥	t	f	21	String	encryptKey		\N	t	t	t	=	t	input	1	2024-07-10 18:03:31.055	1	2024-07-10 18:41:58.449	0
691	39	encrypt_vector	VARCHAR	加密向量	t	f	22	String	encryptVector		\N	t	t	t	=	t	input	1	2024-07-10 18:03:31.068	1	2024-07-10 18:41:58.458	0
692	39	tenant_id	BIGINT	租户ID	t	f	23	Long	tenantId		21915	f	f	f	=	f	input	1	2024-07-10 18:03:31.079	1	2024-07-10 18:41:58.468	0
645	37	error_code	VARCHAR	错误代码	t	f	6	String	errorCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:28.737	1	2024-07-10 18:42:13.41	0
646	37	error_message	VARCHAR	错误信息	t	f	7	String	errorMessage		\N	t	t	t	=	t	input	1	2024-07-10 18:03:28.748	1	2024-07-10 18:42:13.422	0
718	41	create_by	VARCHAR	创建者	t	f	14	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:33.24	1	2024-07-10 18:41:41.834	0
804	47	id	BIGINT	id	f	t	1	Long	id		1154	f	t	f	=	t	input	1	2024-07-10 18:03:39.364	1	2024-07-10 18:40:46.247	0
710	41	max	VARCHAR	指示最大值。\t仅当dataType为int、decimal时生效，逻辑小于等于。	t	f	6	String	max		\N	t	t	t	=	t	input	1	2024-07-10 18:03:32.972	1	2024-07-10 18:41:41.745	0
711	41	maxlength	VARCHAR	指示字符串长度。\t仅当dataType为string时生效。	t	f	7	String	maxlength		\N	t	t	t	=	t	input	1	2024-07-10 18:03:32.988	1	2024-07-10 18:41:41.754	0
712	41	min	VARCHAR	指示最小值。\t仅当dataType为int、decimal时生效，逻辑大于等于。	t	f	8	String	min		\N	t	t	t	=	t	input	1	2024-07-10 18:03:33.002	1	2024-07-10 18:41:41.764	0
713	41	parameter_description	VARCHAR	命令中参数的描述，不影响实际功能，可配置为空字符串""。	t	f	9	String	parameterDescription		随便	t	t	t	=	t	editor	1	2024-07-10 18:03:33.016	1	2024-07-10 18:41:41.774	0
714	41	parameter_name	VARCHAR	命令中参数的名字。	t	f	10	String	parameterName		李四	t	t	t	LIKE	t	input	1	2024-07-10 18:03:33.036	1	2024-07-10 18:41:41.785	0
715	41	required	VARCHAR	指示本条属性是否必填，取值为0或1，默认取值1（必填）。\t目前本字段是非功能性字段，仅起到描述作用。	f	f	11	String	required		\N	t	t	t	=	t	input	1	2024-07-10 18:03:33.141	1	2024-07-10 18:41:41.796	0
716	41	step	VARCHAR	指示步长。	t	f	12	String	step		\N	t	t	t	=	t	input	1	2024-07-10 18:03:33.218	1	2024-07-10 18:41:41.807	0
717	41	unit	VARCHAR	指示单位。\t取值根据参数确定，如：\t•温度单位：“C”或“K”\t•百分比单位：“%”\t•压强单位：“Pa”或“kPa”\t	t	f	13	String	unit		\N	t	t	t	=	t	input	1	2024-07-10 18:03:33.23	1	2024-07-10 18:41:41.817	0
383	26	id	BIGINT	id	f	t	1	Long	id		3669	f	t	f	=	t	input	1	2024-07-10 10:33:36.792	1	2024-07-10 10:33:36.792	1
388	26	did	VARCHAR	设备唯一标识	t	f	2	String	did		29361	t	t	t	=	t	input	1	2024-07-10 10:33:36.859	1	2024-07-10 10:33:36.859	1
391	26	file_url	VARCHAR	文件地址	t	f	3	String	fileUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2024-07-10 10:33:36.909	1	2024-07-10 10:33:36.909	1
393	26	upload_time	TIMESTAMP	上传时间	t	f	4	LocalDateTime	uploadTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:36.983	1	2024-07-10 10:33:36.983	1
397	26	file_name	VARCHAR	文件名称	t	f	5	String	fileName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 10:33:37	1	2024-07-10 10:33:37	1
400	26	file_size	BIGINT	文件大小	t	f	6	Long	fileSize		\N	t	t	t	=	t	input	1	2024-07-10 10:33:37.056	1	2024-07-10 10:33:37.056	1
403	26	remark	VARCHAR	备注	t	f	7	String	remark		随便	t	t	t	=	t	input	1	2024-07-10 10:33:37.12	1	2024-07-10 10:33:37.12	1
406	26	status	SMALLINT	状态[0:成功, 1:未开始, 2:上传中, 3:失败]	t	f	8	Short	status		1	t	t	t	=	t	radio	1	2024-07-10 10:33:37.164	1	2024-07-10 10:33:37.164	1
408	26	created_by	VARCHAR	创建者	t	f	9	String	createdBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:37.188	1	2024-07-10 10:33:37.188	1
410	26	created_time	TIMESTAMP	创建时间	t	f	10	LocalDateTime	createdTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:37.364	1	2024-07-10 10:33:37.364	1
412	26	updated_by	VARCHAR	更新者	t	f	11	String	updatedBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:37.668	1	2024-07-10 10:33:37.668	1
414	26	updated_time	TIMESTAMP	更新时间	t	f	12	LocalDateTime	updatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:37.776	1	2024-07-10 10:33:37.776	1
415	26	tenant_id	BIGINT	租户ID	t	f	13	Long	tenantId		28994	f	f	f	=	f	input	1	2024-07-10 10:33:37.812	1	2024-07-10 10:33:37.812	1
357	23	id	BIGINT	设备ID	f	t	1	Long	id		29712	f	t	f	=	t	input	1	2024-07-10 10:33:35.631	1	2024-07-10 10:33:35.631	1
359	23	group_name	VARCHAR	分组ID	f	f	2	String	groupName		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:33:35.642	1	2024-07-10 10:33:35.642	1
361	23	create_by	VARCHAR	创建者	t	f	3	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:35.652	1	2024-07-10 10:33:35.652	1
362	23	create_time	TIMESTAMP	创建时间	t	f	4	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:35.669	1	2024-07-10 10:33:35.669	1
363	23	update_by	VARCHAR	更新者	t	f	5	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:35.685	1	2024-07-10 10:33:35.685	1
364	23	update_time	TIMESTAMP	更新时间	t	f	6	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:35.723	1	2024-07-10 10:33:35.723	1
365	23	tenant_id	BIGINT	租户ID	t	f	7	Long	tenantId		30647	f	f	f	=	f	input	1	2024-07-10 10:33:35.744	1	2024-07-10 10:33:35.744	1
647	37	start_time	TIMESTAMP	升级开始时间	t	f	8	LocalDateTime	startTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:28.763	1	2024-07-10 18:42:13.431	0
648	37	end_time	TIMESTAMP	升级结束时间	t	f	9	LocalDateTime	endTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:28.779	1	2024-07-10 18:42:13.44	0
649	37	success_details	VARCHAR	升级成功详细信息	t	f	10	String	successDetails		\N	t	t	t	=	t	input	1	2024-07-10 18:03:28.798	1	2024-07-10 18:42:13.451	0
650	37	failure_details	VARCHAR	升级失败详细信息	t	f	11	String	failureDetails		\N	t	t	t	=	t	input	1	2024-07-10 18:03:28.883	1	2024-07-10 18:42:13.461	0
651	37	log_details	VARCHAR	升级过程日志	t	f	12	String	logDetails		\N	t	t	t	=	t	input	1	2024-07-10 18:03:28.971	1	2024-07-10 18:42:13.487	0
652	37	remark	VARCHAR	描述	t	f	13	String	remark		你说的对	t	t	t	=	t	input	1	2024-07-10 18:03:28.985	1	2024-07-10 18:42:13.496	0
653	37	created_time	TIMESTAMP	记录创建时间	f	f	14	LocalDateTime	createdTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:29.003	1	2024-07-10 18:42:13.506	0
488	30	id	BIGINT	id	f	t	1	Long	id		18137	f	t	f	=	t	input	1	2024-07-10 17:47:44.078	1	2024-07-10 17:47:44.078	1
295	19	update_by	VARCHAR	更新者	t	f	15	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:30.219	1	2024-07-10 10:33:30.219	1
296	19	update_time	TIMESTAMP	更新时间	f	f	16	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:30.317	1	2024-07-10 10:33:30.317	1
297	19	remark	VARCHAR	备注	t	f	17	String	remark		你说的对	t	t	t	=	t	input	1	2024-07-10 10:33:30.342	1	2024-07-10 10:33:30.342	1
298	19	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		24321	f	f	f	=	f	input	1	2024-07-10 10:33:30.361	1	2024-07-10 10:33:30.361	1
274	18	sort	BIGINT	排序序号	t	f	3	Long	sort		\N	t	t	t	=	t	input	1	2024-07-10 10:33:28.096	1	2024-07-10 10:33:28.096	1
552	31	product_name	VARCHAR	产品名称	t	f	30	String	productName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 17:52:40.274	1	2024-07-10 17:56:36.726	1
553	31	is_shadow	SMALLINT	是否启用设备影子(0=禁用，1=启用)	t	f	31	Short	isShadow		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.285	1	2024-07-10 17:56:36.74	1
554	31	things_model_value	OTHER	物模型值	t	f	32	Object	thingsModelValue		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.297	1	2024-07-10 17:56:36.75	1
555	31	product_type_id	BIGINT	产品类型ID	t	f	33	Long	productTypeId		5844	t	t	t	=	t	input	1	2024-07-10 17:52:40.309	1	2024-07-10 17:56:36.761	1
556	31	product_type_name	VARCHAR	产品类型名称	t	f	34	String	productTypeName		王五	t	t	t	LIKE	t	input	1	2024-07-10 17:52:40.334	1	2024-07-10 17:56:36.771	1
401	25	processing_time	TIMESTAMP	处理时间	t	f	10	LocalDateTime	processingTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:37.06	1	2024-07-10 10:33:37.06	1
272	18	id	BIGINT	id	f	t	1	Long	id		27207	f	t	f	=	t	input	1	2024-07-10 10:33:28.047	1	2024-07-10 10:33:28.047	1
273	18	name	VARCHAR	名称	f	f	2	String	name		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 10:33:28.069	1	2024-07-10 10:33:28.069	1
263	17	template_code	VARCHAR	产品模版标识	f	f	3	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:26.807	1	2024-07-10 10:33:26.807	1
264	17	template_name	VARCHAR	产品模板名称:自定义，支持中文、英文大小写、数字、下划线和中划线	f	f	4	String	templateName		李四	t	t	t	LIKE	t	input	1	2024-07-10 10:33:26.822	1	2024-07-10 10:33:26.822	1
1	1	id	BIGINT	任务编号	f	t	1	Long	id		1027	f	t	f	=	t	input	1	2024-07-09 11:38:00.12	1	2024-07-09 11:38:00.12	1
2	1	name	VARCHAR	任务名称	f	f	2	String	name		赵六	t	t	t	LIKE	t	input	1	2024-07-09 11:38:00.136	1	2024-07-09 11:38:00.136	1
3	1	status	SMALLINT	任务状态	f	f	3	Short	status		2	t	t	t	=	t	radio	1	2024-07-09 11:38:00.222	1	2024-07-09 11:38:00.222	1
4	1	handler_name	VARCHAR	处理器的名字	f	f	4	String	handlerName		张三	t	t	t	LIKE	t	input	1	2024-07-09 11:38:00.233	1	2024-07-09 11:38:00.233	1
5	1	handler_param	VARCHAR	处理器的参数	t	f	5	String	handlerParam		\N	t	t	t	=	t	input	1	2024-07-09 11:38:00.243	1	2024-07-09 11:38:00.243	1
6	1	cron_expression	VARCHAR	CRON 表达式	f	f	6	String	cronExpression		\N	t	t	t	=	t	input	1	2024-07-09 11:38:00.38	1	2024-07-09 11:38:00.38	1
7	1	retry_count	INTEGER	重试次数	f	f	7	Integer	retryCount		3089	t	t	t	=	t	input	1	2024-07-09 11:38:00.48	1	2024-07-09 11:38:00.48	1
8	1	retry_interval	INTEGER	重试间隔	f	f	8	Integer	retryInterval		\N	t	t	t	=	t	input	1	2024-07-09 11:38:00.491	1	2024-07-09 11:38:00.491	1
9	1	monitor_timeout	INTEGER	监控超时时间	f	f	9	Integer	monitorTimeout		\N	t	t	t	=	t	input	1	2024-07-09 11:38:00.502	1	2024-07-09 11:38:00.502	1
10	1	creator	VARCHAR	创建者	t	f	10	String	creator		\N	f	f	f	=	f	input	1	2024-07-09 11:38:00.511	1	2024-07-09 11:38:00.511	1
11	1	create_time	TIMESTAMP	创建时间	f	f	11	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-09 11:38:00.524	1	2024-07-09 11:38:00.524	1
12	1	updater	VARCHAR	更新者	t	f	12	String	updater		\N	f	f	f	=	f	input	1	2024-07-09 11:38:00.535	1	2024-07-09 11:38:00.535	1
13	1	update_time	TIMESTAMP	更新时间	f	f	13	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-09 11:38:00.549	1	2024-07-09 11:38:00.549	1
14	1	deleted	SMALLINT	是否删除	f	f	14	Short	deleted		\N	f	f	f	=	f	input	1	2024-07-09 11:38:00.575	1	2024-07-09 11:38:00.575	1
299	20	id	BIGINT	id	f	t	1	Long	id		10309	f	t	f	=	t	input	1	2024-07-10 10:33:30.839	1	2024-07-10 10:33:30.839	1
300	20	did	VARCHAR	设备唯一标识	f	f	2	String	did		13637	t	t	t	=	t	input	1	2024-07-10 10:33:30.854	1	2024-07-10 10:33:30.854	1
301	20	name	VARCHAR	设备名称	t	f	3	String	name		李四	t	t	t	LIKE	t	input	1	2024-07-10 10:33:30.88	1	2024-07-10 10:33:30.88	1
302	20	description	VARCHAR	设备描述	t	f	4	String	description		随便	t	t	t	=	t	editor	1	2024-07-10 10:33:30.915	1	2024-07-10 10:33:30.915	1
303	20	enabled_status	VARCHAR	设备状态： ENABLE:启用 || DISABLE:禁用	t	f	5	String	enabledStatus		1	t	t	t	=	t	radio	1	2024-07-10 10:33:30.934	1	2024-07-10 10:33:30.934	1
304	20	connect_status	VARCHAR	连接状态 :    OFFLINE:离线 || ONLINE:在线	t	f	6	String	connectStatus		1	t	t	t	=	t	radio	1	2024-07-10 10:33:30.946	1	2024-07-10 10:33:30.946	1
305	20	pid	VARCHAR	产品唯一标识	f	f	7	String	pid		31091	t	t	t	=	t	input	1	2024-07-10 10:33:30.972	1	2024-07-10 10:33:30.972	1
306	20	create_by	VARCHAR	创建者	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:30.986	1	2024-07-10 10:33:30.986	1
307	20	create_time	TIMESTAMP	创建时间	t	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:31.009	1	2024-07-10 10:33:31.009	1
308	20	update_by	VARCHAR	更新者	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:31.037	1	2024-07-10 10:33:31.037	1
309	20	update_time	TIMESTAMP	更新时间	t	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:31.052	1	2024-07-10 10:33:31.052	1
310	20	remark	VARCHAR	备注	t	f	12	String	remark		你猜	t	t	t	=	t	input	1	2024-07-10 10:33:31.064	1	2024-07-10 10:33:31.064	1
311	20	device_version	VARCHAR	设备版本号	t	f	13	String	deviceVersion		\N	t	t	t	=	t	input	1	2024-07-10 10:33:31.075	1	2024-07-10 10:33:31.075	1
265	17	status	VARCHAR	状态(字典值：启用  停用)	f	f	5	String	status		1	t	t	t	=	t	radio	1	2024-07-10 10:33:26.833	1	2024-07-10 10:33:26.833	1
266	17	remark	VARCHAR	产品模型模板描述	t	f	6	String	remark		你说的对	t	t	t	=	t	input	1	2024-07-10 10:33:26.862	1	2024-07-10 10:33:26.862	1
267	17	create_by	VARCHAR	创建者	t	f	7	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:26.876	1	2024-07-10 10:33:26.876	1
268	17	create_time	TIMESTAMP	创建时间	f	f	8	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:26.969	1	2024-07-10 10:33:26.969	1
269	17	update_by	VARCHAR	更新者	t	f	9	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:27.016	1	2024-07-10 10:33:27.016	1
270	17	update_time	TIMESTAMP	更新时间	f	f	10	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:27.032	1	2024-07-10 10:33:27.032	1
312	20	device_sn	VARCHAR	设备sn号	f	f	14	String	deviceSn		\N	t	t	t	=	t	input	1	2024-07-10 10:33:31.123	1	2024-07-10 10:33:31.123	1
313	20	ip_address	VARCHAR	IP地址	t	f	15	String	ipAddress		\N	t	t	t	=	t	input	1	2024-07-10 10:33:31.136	1	2024-07-10 10:33:31.136	1
314	20	mac_address	VARCHAR	MAC地址	t	f	16	String	macAddress		\N	t	t	t	=	t	input	1	2024-07-10 10:33:31.163	1	2024-07-10 10:33:31.163	1
315	20	active_status	SMALLINT	激活状态 0:未激活 1:已激活	t	f	17	Short	activeStatus		1	t	t	t	=	t	radio	1	2024-07-10 10:33:31.176	1	2024-07-10 10:33:31.176	1
316	20	extension	VARCHAR	扩展json	t	f	18	String	extension		\N	t	t	t	=	t	input	1	2024-07-10 10:33:31.223	1	2024-07-10 10:33:31.223	1
317	20	activated_time	TIMESTAMP	激活时间	t	f	19	LocalDateTime	activatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:31.259	1	2024-07-10 10:33:31.259	1
318	20	last_online_time	TIMESTAMP	最后上线时间	t	f	20	LocalDateTime	lastOnlineTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:31.338	1	2024-07-10 10:33:31.338	1
319	20	parent_did	VARCHAR	子设备关联网关的设备唯一标识	t	f	21	String	parentDid		17449	t	t	t	=	t	input	1	2024-07-10 10:33:31.353	1	2024-07-10 10:33:31.353	1
320	20	device_type	VARCHAR	支持以下两种产品类型\t•COMMON：普通产品，需直连设备。\t•GATEWAY：网关产品，可挂载子设备。\t•SUBSET：子设备。	t	f	22	String	deviceType		1	t	t	t	=	t	select	1	2024-07-10 10:33:31.366	1	2024-07-10 10:33:31.366	1
321	20	latitude	NUMERIC	纬度	t	f	23	BigDecimal	latitude		\N	t	t	t	=	t	input	1	2024-07-10 10:33:31.454	1	2024-07-10 10:33:31.454	1
322	20	longitude	NUMERIC	经度	t	f	24	BigDecimal	longitude		\N	t	t	t	=	t	input	1	2024-07-10 10:33:31.599	1	2024-07-10 10:33:31.599	1
323	20	location_name	VARCHAR	设备所在位置	t	f	25	String	locationName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 10:33:32.462	1	2024-07-10 10:33:32.462	1
324	20	province_code	VARCHAR	省,直辖市编码	t	f	26	String	provinceCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:32.858	1	2024-07-10 10:33:32.858	1
325	20	city_code	VARCHAR	市编码	t	f	27	String	cityCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:33.187	1	2024-07-10 10:33:33.187	1
326	20	region_code	VARCHAR	区县	t	f	28	String	regionCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:33.219	1	2024-07-10 10:33:33.219	1
327	20	tenant_id	BIGINT	租户ID	t	f	29	Long	tenantId		20512	f	f	f	=	f	input	1	2024-07-10 10:33:33.308	1	2024-07-10 10:33:33.308	1
328	20	product_name	VARCHAR	产品名称	t	f	30	String	productName		李四	t	t	t	LIKE	t	input	1	2024-07-10 10:33:33.369	1	2024-07-10 10:33:33.369	1
330	20	is_shadow	SMALLINT	是否启用设备影子(0=禁用，1=启用)	t	f	31	Short	isShadow		\N	t	t	t	=	t	input	1	2024-07-10 10:33:33.429	1	2024-07-10 10:33:33.429	1
332	20	things_model_value	OTHER	物模型值	t	f	32	Object	thingsModelValue		\N	t	t	t	=	t	input	1	2024-07-10 10:33:33.47	1	2024-07-10 10:33:33.47	1
334	20	product_type_id	BIGINT	产品类型ID	t	f	33	Long	productTypeId		21497	t	t	t	=	t	input	1	2024-07-10 10:33:33.525	1	2024-07-10 10:33:33.525	1
336	20	product_type_name	VARCHAR	产品类型名称	t	f	34	String	productTypeName		张三	t	t	t	LIKE	t	input	1	2024-07-10 10:33:33.591	1	2024-07-10 10:33:33.591	1
338	20	group_id	BIGINT	分组ID	t	f	35	Long	groupId		3640	t	t	t	=	t	input	1	2024-07-10 10:33:33.618	1	2024-07-10 10:33:33.618	1
427	27	condition_type	SMALLINT	条件类型(0:匹配设备触发、1:指定设备触发、2:按策略定时触发)	f	f	3	Short	conditionType		2	t	t	t	=	t	select	1	2024-07-10 10:33:39.047	1	2024-07-10 10:33:39.047	1
428	27	did	VARCHAR	设备标识(匹配设备设备类型存储一个产品下所有的设备标识逗号分隔，指定设备触发存储指定的设备标识)	t	f	4	String	did		23118	t	t	t	=	t	input	1	2024-07-10 10:33:39.102	1	2024-07-10 10:33:39.102	1
430	27	service_id	BIGINT	服务ID	t	f	6	Long	serviceId		25308	t	t	t	=	t	input	1	2024-07-10 10:33:39.462	1	2024-07-10 10:33:39.462	1
431	27	properties_id	BIGINT	属性ID	t	f	7	Long	propertiesId		15591	t	t	t	=	t	input	1	2024-07-10 10:33:39.645	1	2024-07-10 10:33:39.645	1
432	27	comparison_mode	VARCHAR	比较模式\t<\t<=\t>\t>=\t==\t!=\tin\tbetween	t	f	8	String	comparisonMode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:39.66	1	2024-07-10 10:33:39.66	1
433	27	comparison_value	VARCHAR	比较值\t\tbetween类型传值例子  [10,15] 必须是两位，且数字不能重复\t判断数据是否处于一个离散的取值范围内，例如输入[1,2,3,4]，取值范围是1、2、3、4四个值，如果比较值类型为float(double)，两个float（double）型数值相差在0.000001范围内即为相等	t	f	9	String	comparisonValue		\N	t	t	t	=	t	input	1	2024-07-10 10:33:39.717	1	2024-07-10 10:33:39.717	1
434	27	create_by	VARCHAR	创建人	t	f	10	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:39.735	1	2024-07-10 10:33:39.735	1
435	27	create_time	TIMESTAMP	创建时间	f	f	11	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:39.745	1	2024-07-10 10:33:39.745	1
436	27	update_by	VARCHAR	更新人	t	f	12	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:39.762	1	2024-07-10 10:33:39.762	1
437	27	update_time	TIMESTAMP	更新时间	f	f	13	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:39.782	1	2024-07-10 10:33:39.782	1
438	27	tenant_id	BIGINT	租户ID	t	f	14	Long	tenantId		18784	f	f	f	=	f	input	1	2024-07-10 10:33:39.821	1	2024-07-10 10:33:39.821	1
29	2	ip_address	VARCHAR	IP地址	t	f	15	String	ipAddress		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.679	1	2024-07-10 10:32:59.679	1
30	2	mac_address	VARCHAR	MAC地址	t	f	16	String	macAddress		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.696	1	2024-07-10 10:32:59.696	1
31	2	active_status	SMALLINT	激活状态 0:未激活 1:已激活	t	f	17	Short	activeStatus		1	t	t	t	=	t	radio	1	2024-07-10 10:32:59.711	1	2024-07-10 10:32:59.711	1
32	2	extension	VARCHAR	扩展json	t	f	18	String	extension		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.731	1	2024-07-10 10:32:59.731	1
33	2	activated_time	TIMESTAMP	激活时间	t	f	19	LocalDateTime	activatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:32:59.749	1	2024-07-10 10:32:59.749	1
34	2	last_online_time	TIMESTAMP	最后上线时间	t	f	20	LocalDateTime	lastOnlineTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:32:59.77	1	2024-07-10 10:32:59.77	1
35	2	parent_did	VARCHAR	子设备关联网关的设备唯一标识	t	f	21	String	parentDid		20968	t	t	t	=	t	input	1	2024-07-10 10:32:59.779	1	2024-07-10 10:32:59.779	1
36	2	device_type	VARCHAR	支持以下两种产品类型\t•COMMON：普通产品，需直连设备。\t•GATEWAY：网关产品，可挂载子设备。\t•SUBSET：子设备。	t	f	22	String	deviceType		1	t	t	t	=	t	select	1	2024-07-10 10:32:59.793	1	2024-07-10 10:32:59.793	1
37	2	latitude	NUMERIC	纬度	t	f	23	BigDecimal	latitude		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.805	1	2024-07-10 10:32:59.805	1
38	2	longitude	NUMERIC	经度	t	f	24	BigDecimal	longitude		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.824	1	2024-07-10 10:32:59.824	1
39	2	location_name	VARCHAR	设备所在位置	t	f	25	String	locationName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 10:32:59.833	1	2024-07-10 10:32:59.833	1
40	2	province_code	VARCHAR	省,直辖市编码	t	f	26	String	provinceCode		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.844	1	2024-07-10 10:32:59.844	1
41	2	city_code	VARCHAR	市编码	t	f	27	String	cityCode		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.855	1	2024-07-10 10:32:59.855	1
42	2	region_code	VARCHAR	区县	t	f	28	String	regionCode		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.891	1	2024-07-10 10:32:59.891	1
43	2	tenant_id	BIGINT	租户ID	t	f	29	Long	tenantId		9853	f	f	f	=	f	input	1	2024-07-10 10:32:59.905	1	2024-07-10 10:32:59.905	1
44	2	product_name	VARCHAR	产品名称	t	f	30	String	productName		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:32:59.937	1	2024-07-10 10:32:59.937	1
45	2	is_shadow	SMALLINT	是否启用设备影子(0=禁用，1=启用)	t	f	31	Short	isShadow		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.946	1	2024-07-10 10:32:59.946	1
46	2	things_model_value	OTHER	物模型值	t	f	32	Object	thingsModelValue		\N	t	t	t	=	t	input	1	2024-07-10 10:32:59.957	1	2024-07-10 10:32:59.957	1
47	2	product_type_id	BIGINT	产品类型ID	t	f	33	Long	productTypeId		8598	t	t	t	=	t	input	1	2024-07-10 10:32:59.983	1	2024-07-10 10:32:59.983	1
48	2	product_type_name	VARCHAR	产品类型名称	t	f	34	String	productTypeName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 10:32:59.993	1	2024-07-10 10:32:59.993	1
49	2	group_id	BIGINT	分组ID	t	f	35	Long	groupId		31817	t	t	t	=	t	input	1	2024-07-10 10:33:00.005	1	2024-07-10 10:33:00.005	1
607	34	status	SMALLINT	状态[0:成功, 1:未开始, 2:上传中, 3:失败]	t	f	8	Short	status		2	t	t	t	=	t	radio	1	2024-07-10 18:03:25.397	1	2024-07-10 18:42:41.434	0
608	34	created_by	VARCHAR	创建者	t	f	9	String	createdBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:25.41	1	2024-07-10 18:42:41.45	0
609	34	created_time	TIMESTAMP	创建时间	t	f	10	LocalDateTime	createdTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:25.42	1	2024-07-10 18:42:41.459	0
610	34	updated_by	VARCHAR	更新者	t	f	11	String	updatedBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:25.438	1	2024-07-10 18:42:41.467	0
598	33	update_time	TIMESTAMP	更新时间	t	f	6	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:24.579	1	2024-07-10 18:42:50.217	0
599	33	tenant_id	BIGINT	租户ID	t	f	7	Long	tenantId		2670	f	f	f	=	f	input	1	2024-07-10 18:03:24.601	1	2024-07-10 18:42:50.231	0
734	42	step	VARCHAR	指示步长。	t	f	12	String	step		\N	t	t	t	=	t	input	1	2024-07-10 18:03:34.397	1	2024-07-10 18:41:33.453	0
735	42	unit	VARCHAR	指示单位。\t取值根据参数确定，如：\t•温度单位：“C”或“K”\t•百分比单位：“%”\t•压强单位：“Pa”或“kPa”\t	t	f	13	String	unit		\N	t	t	t	=	t	input	1	2024-07-10 18:03:34.408	1	2024-07-10 18:41:33.465	0
736	42	create_by	VARCHAR	创建者	t	f	14	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:34.418	1	2024-07-10 18:41:33.474	0
474	29	id	BIGINT	主键	f	t	1	Long	id		23866	f	t	f	=	t	input	1	2024-07-10 17:47:27.359	1	2024-07-10 17:47:27.359	1
475	29	rule_id	BIGINT	规则ID	f	f	2	Long	ruleId		19905	t	t	t	=	t	input	1	2024-07-10 17:47:27.376	1	2024-07-10 17:47:27.376	1
476	29	condition_type	SMALLINT	条件类型(0:匹配设备触发、1:指定设备触发、2:按策略定时触发)	f	f	3	Short	conditionType		1	t	t	t	=	t	select	1	2024-07-10 17:47:27.464	1	2024-07-10 17:47:27.464	1
477	29	did	VARCHAR	设备标识(匹配设备设备类型存储一个产品下所有的设备标识逗号分隔，指定设备触发存储指定的设备标识)	t	f	4	String	did		1917	t	t	t	=	t	input	1	2024-07-10 17:47:27.566	1	2024-07-10 17:47:27.566	1
478	29	pid	VARCHAR	产品标识	t	f	5	String	pid		12169	t	t	t	=	t	input	1	2024-07-10 17:47:27.583	1	2024-07-10 17:47:27.583	1
479	29	service_id	BIGINT	服务ID	t	f	6	Long	serviceId		2705	t	t	t	=	t	input	1	2024-07-10 17:47:27.599	1	2024-07-10 17:47:27.599	1
523	31	id	BIGINT	id	f	t	1	Long	id		9480	f	t	f	=	t	input	1	2024-07-10 17:52:39.691	1	2024-07-10 17:56:36.099	1
524	31	did	VARCHAR	设备唯一标识	f	f	2	String	did		13800	t	t	t	=	t	input	1	2024-07-10 17:52:39.713	1	2024-07-10 17:56:36.111	1
525	31	name	VARCHAR	设备名称	t	f	3	String	name		张三	t	t	t	LIKE	t	input	1	2024-07-10 17:52:39.724	1	2024-07-10 17:56:36.128	1
737	42	create_time	TIMESTAMP	创建时间	f	f	15	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:34.43	1	2024-07-10 18:41:33.489	0
743	43	event_code	VARCHAR	事件code	f	f	3	String	eventCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:35.37	1	2024-07-10 18:41:25.447	0
744	43	event_type	VARCHAR	事件类型。\tINFO_EVENT_TYPE：信息。\tALERT_EVENT_TYPE：告警。\tERROR_EVENT_TYPE：故障	f	f	4	String	eventType		1	t	t	t	=	t	select	1	2024-07-10 18:03:35.38	1	2024-07-10 18:41:25.462	0
745	43	template_code	VARCHAR	模板code	t	f	5	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:35.391	1	2024-07-10 18:41:25.472	0
746	43	pid	VARCHAR	产品唯一标识	t	f	6	String	pid		28685	t	t	t	=	t	input	1	2024-07-10 18:03:35.419	1	2024-07-10 18:41:25.483	0
747	43	enabled_status	VARCHAR	状态(字典值：0启用  1停用)	t	f	7	String	enabledStatus		2	t	t	t	=	t	radio	1	2024-07-10 18:03:35.499	1	2024-07-10 18:41:25.498	0
733	42	required	VARCHAR	指示本条属性是否必填，取值为0或1，默认取值1（必填）。\t目前本字段是非功能性字段，仅起到描述作用。	f	f	11	String	required		\N	t	t	t	=	t	input	1	2024-07-10 18:03:34.377	1	2024-07-10 18:41:33.437	0
738	42	update_by	VARCHAR	更新者	t	f	16	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:34.544	1	2024-07-10 18:41:33.498	0
739	42	update_time	TIMESTAMP	更新时间	f	f	17	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:34.655	1	2024-07-10 18:41:33.508	0
740	42	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		16499	f	f	f	=	f	input	1	2024-07-10 18:03:34.666	1	2024-07-10 18:41:33.629	0
741	43	id	BIGINT	id	f	t	1	Long	id		28979	f	t	f	=	t	input	1	2024-07-10 18:03:35.331	1	2024-07-10 18:41:25.42	0
742	43	event_name	VARCHAR	事件名称	f	f	2	String	eventName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 18:03:35.355	1	2024-07-10 18:41:25.433	0
601	34	did	VARCHAR	设备唯一标识	t	f	2	String	did		418	t	t	t	=	t	input	1	2024-07-10 18:03:25.26	1	2024-07-10 18:42:41.362	0
765	44	step	VARCHAR	指示步长。	t	f	12	String	step		\N	t	t	t	=	t	input	1	2024-07-10 18:03:36.706	1	2024-07-10 18:41:16.129	0
613	35	id	BIGINT	id	f	t	1	Long	id		9811	f	t	f	=	t	input	1	2024-07-10 18:03:26.355	1	2024-07-10 18:42:31.74	0
614	35	did	VARCHAR	设备标识	f	f	2	String	did		28344	t	t	t	=	t	input	1	2024-07-10 18:03:26.369	1	2024-07-10 18:42:31.759	0
439	28	id	BIGINT	id	f	t	1	Long	id		17542	f	t	f	=	t	input	1	2024-07-10 17:46:57.549	1	2024-07-10 17:46:57.549	1
440	28	did	VARCHAR	设备唯一标识	f	f	2	String	did		18214	t	t	t	=	t	input	1	2024-07-10 17:46:57.572	1	2024-07-10 17:46:57.572	1
441	28	name	VARCHAR	设备名称	t	f	3	String	name		赵六	t	t	t	LIKE	t	input	1	2024-07-10 17:46:57.585	1	2024-07-10 17:46:57.585	1
442	28	description	VARCHAR	设备描述	t	f	4	String	description		你猜	t	t	t	=	t	editor	1	2024-07-10 17:46:57.596	1	2024-07-10 17:46:57.596	1
443	28	enabled_status	VARCHAR	设备状态： ENABLE:启用 || DISABLE:禁用	t	f	5	String	enabledStatus		1	t	t	t	=	t	radio	1	2024-07-10 17:46:57.616	1	2024-07-10 17:46:57.616	1
444	28	connect_status	VARCHAR	连接状态 :    OFFLINE:离线 || ONLINE:在线	t	f	6	String	connectStatus		2	t	t	t	=	t	radio	1	2024-07-10 17:46:57.627	1	2024-07-10 17:46:57.627	1
445	28	pid	VARCHAR	产品唯一标识	f	f	7	String	pid		16921	t	t	t	=	t	input	1	2024-07-10 17:46:57.638	1	2024-07-10 17:46:57.638	1
446	28	create_by	VARCHAR	创建者	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 17:46:57.651	1	2024-07-10 17:46:57.651	1
447	28	create_time	TIMESTAMP	创建时间	t	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 17:46:57.664	1	2024-07-10 17:46:57.664	1
448	28	update_by	VARCHAR	更新者	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 17:46:57.677	1	2024-07-10 17:46:57.677	1
449	28	update_time	TIMESTAMP	更新时间	t	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 17:46:57.688	1	2024-07-10 17:46:57.688	1
450	28	remark	VARCHAR	备注	t	f	12	String	remark		你猜	t	t	t	=	t	input	1	2024-07-10 17:46:57.698	1	2024-07-10 17:46:57.698	1
451	28	device_version	VARCHAR	设备版本号	t	f	13	String	deviceVersion		\N	t	t	t	=	t	input	1	2024-07-10 17:46:57.71	1	2024-07-10 17:46:57.71	1
452	28	device_sn	VARCHAR	设备sn号	f	f	14	String	deviceSn		\N	t	t	t	=	t	input	1	2024-07-10 17:46:57.723	1	2024-07-10 17:46:57.723	1
453	28	ip_address	VARCHAR	IP地址	t	f	15	String	ipAddress		\N	t	t	t	=	t	input	1	2024-07-10 17:46:57.735	1	2024-07-10 17:46:57.735	1
454	28	mac_address	VARCHAR	MAC地址	t	f	16	String	macAddress		\N	t	t	t	=	t	input	1	2024-07-10 17:46:57.746	1	2024-07-10 17:46:57.746	1
455	28	active_status	SMALLINT	激活状态 0:未激活 1:已激活	t	f	17	Short	activeStatus		2	t	t	t	=	t	radio	1	2024-07-10 17:46:57.871	1	2024-07-10 17:46:57.871	1
456	28	extension	VARCHAR	扩展json	t	f	18	String	extension		\N	t	t	t	=	t	input	1	2024-07-10 17:46:57.95	1	2024-07-10 17:46:57.95	1
457	28	activated_time	TIMESTAMP	激活时间	t	f	19	LocalDateTime	activatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 17:46:57.969	1	2024-07-10 17:46:57.969	1
458	28	last_online_time	TIMESTAMP	最后上线时间	t	f	20	LocalDateTime	lastOnlineTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 17:46:57.986	1	2024-07-10 17:46:57.986	1
459	28	parent_did	VARCHAR	子设备关联网关的设备唯一标识	t	f	21	String	parentDid		11346	t	t	t	=	t	input	1	2024-07-10 17:46:58.001	1	2024-07-10 17:46:58.001	1
460	28	device_type	VARCHAR	支持以下两种产品类型\t•COMMON：普通产品，需直连设备。\t•GATEWAY：网关产品，可挂载子设备。\t•SUBSET：子设备。	t	f	22	String	deviceType		2	t	t	t	=	t	select	1	2024-07-10 17:46:58.016	1	2024-07-10 17:46:58.016	1
461	28	latitude	NUMERIC	纬度	t	f	23	BigDecimal	latitude		\N	t	t	t	=	t	input	1	2024-07-10 17:46:58.029	1	2024-07-10 17:46:58.029	1
462	28	longitude	NUMERIC	经度	t	f	24	BigDecimal	longitude		\N	t	t	t	=	t	input	1	2024-07-10 17:46:58.041	1	2024-07-10 17:46:58.041	1
463	28	location_name	VARCHAR	设备所在位置	t	f	25	String	locationName		李四	t	t	t	LIKE	t	input	1	2024-07-10 17:46:58.052	1	2024-07-10 17:46:58.052	1
464	28	province_code	VARCHAR	省,直辖市编码	t	f	26	String	provinceCode		\N	t	t	t	=	t	input	1	2024-07-10 17:46:58.063	1	2024-07-10 17:46:58.063	1
465	28	city_code	VARCHAR	市编码	t	f	27	String	cityCode		\N	t	t	t	=	t	input	1	2024-07-10 17:46:58.088	1	2024-07-10 17:46:58.088	1
466	28	region_code	VARCHAR	区县	t	f	28	String	regionCode		\N	t	t	t	=	t	input	1	2024-07-10 17:46:58.105	1	2024-07-10 17:46:58.105	1
467	28	tenant_id	BIGINT	租户ID	t	f	29	Long	tenantId		10128	f	f	f	=	f	input	1	2024-07-10 17:46:58.115	1	2024-07-10 17:46:58.115	1
468	28	product_name	VARCHAR	产品名称	t	f	30	String	productName		李四	t	t	t	LIKE	t	input	1	2024-07-10 17:46:58.129	1	2024-07-10 17:46:58.129	1
469	28	is_shadow	SMALLINT	是否启用设备影子(0=禁用，1=启用)	t	f	31	Short	isShadow		\N	t	t	t	=	t	input	1	2024-07-10 17:46:58.14	1	2024-07-10 17:46:58.14	1
470	28	things_model_value	OTHER	物模型值	t	f	32	Object	thingsModelValue		\N	t	t	t	=	t	input	1	2024-07-10 17:46:58.152	1	2024-07-10 17:46:58.152	1
471	28	product_type_id	BIGINT	产品类型ID	t	f	33	Long	productTypeId		29007	t	t	t	=	t	input	1	2024-07-10 17:46:58.163	1	2024-07-10 17:46:58.163	1
472	28	product_type_name	VARCHAR	产品类型名称	t	f	34	String	productTypeName		李四	t	t	t	LIKE	t	input	1	2024-07-10 17:46:58.175	1	2024-07-10 17:46:58.175	1
473	28	group_id	BIGINT	分组ID	t	f	35	Long	groupId		23100	t	t	t	=	t	input	1	2024-07-10 17:46:58.189	1	2024-07-10 17:46:58.189	1
489	30	did	VARCHAR	设备唯一标识	f	f	2	String	did		25141	t	t	t	=	t	input	1	2024-07-10 17:47:44.099	1	2024-07-10 17:47:44.099	1
490	30	name	VARCHAR	设备名称	t	f	3	String	name		赵六	t	t	t	LIKE	t	input	1	2024-07-10 17:47:44.109	1	2024-07-10 17:47:44.109	1
491	30	description	VARCHAR	设备描述	t	f	4	String	description		你说的对	t	t	t	=	t	editor	1	2024-07-10 17:47:44.12	1	2024-07-10 17:47:44.12	1
492	30	enabled_status	VARCHAR	设备状态： ENABLE:启用 || DISABLE:禁用	t	f	5	String	enabledStatus		1	t	t	t	=	t	radio	1	2024-07-10 17:47:44.13	1	2024-07-10 17:47:44.13	1
493	30	connect_status	VARCHAR	连接状态 :    OFFLINE:离线 || ONLINE:在线	t	f	6	String	connectStatus		1	t	t	t	=	t	radio	1	2024-07-10 17:47:44.14	1	2024-07-10 17:47:44.14	1
494	30	pid	VARCHAR	产品唯一标识	f	f	7	String	pid		24023	t	t	t	=	t	input	1	2024-07-10 17:47:44.149	1	2024-07-10 17:47:44.149	1
495	30	create_by	VARCHAR	创建者	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.171	1	2024-07-10 17:47:44.171	1
496	30	create_time	TIMESTAMP	创建时间	t	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 17:47:44.185	1	2024-07-10 17:47:44.185	1
497	30	update_by	VARCHAR	更新者	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.201	1	2024-07-10 17:47:44.201	1
498	30	update_time	TIMESTAMP	更新时间	t	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 17:47:44.213	1	2024-07-10 17:47:44.213	1
499	30	remark	VARCHAR	备注	t	f	12	String	remark		你说的对	t	t	t	=	t	input	1	2024-07-10 17:47:44.222	1	2024-07-10 17:47:44.222	1
500	30	device_version	VARCHAR	设备版本号	t	f	13	String	deviceVersion		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.234	1	2024-07-10 17:47:44.234	1
501	30	device_sn	VARCHAR	设备sn号	f	f	14	String	deviceSn		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.247	1	2024-07-10 17:47:44.247	1
502	30	ip_address	VARCHAR	IP地址	t	f	15	String	ipAddress		\N	t	t	t	=	t	input	1	2024-07-10 17:47:44.256	1	2024-07-10 17:47:44.256	1
480	29	properties_id	BIGINT	属性ID	t	f	7	Long	propertiesId		20259	t	t	t	=	t	input	1	2024-07-10 17:47:27.61	1	2024-07-10 17:47:27.61	1
481	29	comparison_mode	VARCHAR	比较模式\t<\t<=\t>\t>=\t==\t!=\tin\tbetween	t	f	8	String	comparisonMode		\N	t	t	t	=	t	input	1	2024-07-10 17:47:27.621	1	2024-07-10 17:47:27.621	1
482	29	comparison_value	VARCHAR	比较值\t\tbetween类型传值例子  [10,15] 必须是两位，且数字不能重复\t判断数据是否处于一个离散的取值范围内，例如输入[1,2,3,4]，取值范围是1、2、3、4四个值，如果比较值类型为float(double)，两个float（double）型数值相差在0.000001范围内即为相等	t	f	9	String	comparisonValue		\N	t	t	t	=	t	input	1	2024-07-10 17:47:27.633	1	2024-07-10 17:47:27.633	1
483	29	create_by	VARCHAR	创建人	t	f	10	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 17:47:27.643	1	2024-07-10 17:47:27.643	1
484	29	create_time	TIMESTAMP	创建时间	f	f	11	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 17:47:27.656	1	2024-07-10 17:47:27.656	1
485	29	update_by	VARCHAR	更新人	t	f	12	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 17:47:27.667	1	2024-07-10 17:47:27.667	1
486	29	update_time	TIMESTAMP	更新时间	f	f	13	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 17:47:27.682	1	2024-07-10 17:47:27.682	1
487	29	tenant_id	BIGINT	租户ID	t	f	14	Long	tenantId		16305	f	f	f	=	f	input	1	2024-07-10 17:47:27.693	1	2024-07-10 17:47:27.693	1
526	31	description	VARCHAR	设备描述	t	f	4	String	description		随便	t	t	t	=	t	editor	1	2024-07-10 17:52:39.735	1	2024-07-10 17:56:36.142	1
527	31	enabled_status	VARCHAR	设备状态： ENABLE:启用 || DISABLE:禁用	t	f	5	String	enabledStatus		1	t	t	t	=	t	radio	1	2024-07-10 17:52:39.75	1	2024-07-10 17:56:36.155	1
528	31	connect_status	VARCHAR	连接状态 :    OFFLINE:离线 || ONLINE:在线	t	f	6	String	connectStatus		2	t	t	t	=	t	radio	1	2024-07-10 17:52:39.769	1	2024-07-10 17:56:36.184	1
529	31	pid	VARCHAR	产品唯一标识	f	f	7	String	pid		3087	t	t	t	=	t	input	1	2024-07-10 17:52:39.885	1	2024-07-10 17:56:36.202	1
530	31	create_by	VARCHAR	创建者	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 17:52:39.939	1	2024-07-10 17:56:36.222	1
531	31	create_time	TIMESTAMP	创建时间	t	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 17:52:39.956	1	2024-07-10 17:56:36.235	1
532	31	update_by	VARCHAR	更新者	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 17:52:39.973	1	2024-07-10 17:56:36.247	1
533	31	update_time	TIMESTAMP	更新时间	t	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 17:52:39.989	1	2024-07-10 17:56:36.258	1
534	31	remark	VARCHAR	备注	t	f	12	String	remark		随便	t	t	t	=	t	input	1	2024-07-10 17:52:40	1	2024-07-10 17:56:36.27	1
535	31	device_version	VARCHAR	设备版本号	t	f	13	String	deviceVersion		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.02	1	2024-07-10 17:56:36.284	1
536	31	device_sn	VARCHAR	设备sn号	f	f	14	String	deviceSn		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.043	1	2024-07-10 17:56:36.306	1
537	31	ip_address	VARCHAR	IP地址	t	f	15	String	ipAddress		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.056	1	2024-07-10 17:56:36.319	1
538	31	mac_address	VARCHAR	MAC地址	t	f	16	String	macAddress		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.069	1	2024-07-10 17:56:36.331	1
539	31	active_status	SMALLINT	激活状态 0:未激活 1:已激活	t	f	17	Short	activeStatus		1	t	t	t	=	t	radio	1	2024-07-10 17:52:40.081	1	2024-07-10 17:56:36.36	1
540	31	extension	VARCHAR	扩展json	t	f	18	String	extension		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.094	1	2024-07-10 17:56:36.373	1
541	31	activated_time	TIMESTAMP	激活时间	t	f	19	LocalDateTime	activatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 17:52:40.108	1	2024-07-10 17:56:36.387	1
542	31	last_online_time	TIMESTAMP	最后上线时间	t	f	20	LocalDateTime	lastOnlineTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 17:52:40.123	1	2024-07-10 17:56:36.412	1
543	31	parent_did	VARCHAR	子设备关联网关的设备唯一标识	t	f	21	String	parentDid		24799	t	t	t	=	t	input	1	2024-07-10 17:52:40.145	1	2024-07-10 17:56:36.512	1
544	31	device_type	VARCHAR	支持以下两种产品类型\t•COMMON：普通产品，需直连设备。\t•GATEWAY：网关产品，可挂载子设备。\t•SUBSET：子设备。	t	f	22	String	deviceType		1	t	t	t	=	t	select	1	2024-07-10 17:52:40.157	1	2024-07-10 17:56:36.558	1
545	31	latitude	NUMERIC	纬度	t	f	23	BigDecimal	latitude		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.172	1	2024-07-10 17:56:36.644	1
546	31	longitude	NUMERIC	经度	t	f	24	BigDecimal	longitude		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.184	1	2024-07-10 17:56:36.658	1
547	31	location_name	VARCHAR	设备所在位置	t	f	25	String	locationName		王五	t	t	t	LIKE	t	input	1	2024-07-10 17:52:40.195	1	2024-07-10 17:56:36.669	1
548	31	province_code	VARCHAR	省,直辖市编码	t	f	26	String	provinceCode		\N	t	t	t	=	t	input	1	2024-07-10 17:52:40.21	1	2024-07-10 17:56:36.68	1
259	16	update_time	TIMESTAMP	更新时间	f	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:25.324	1	2024-07-10 10:33:25.324	1
260	16	tenant_id	BIGINT	租户ID	t	f	12	Long	tenantId		5905	f	f	f	=	f	input	1	2024-07-10 10:33:25.339	1	2024-07-10 10:33:25.339	1
230	15	property_name	VARCHAR	功能名称	f	f	2	String	propertyName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 10:33:23.614	1	2024-07-10 10:33:23.614	1
231	15	property_code	VARCHAR	属性code	f	f	3	String	propertyCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:23.653	1	2024-07-10 10:33:23.653	1
239	15	required	INTEGER	指示本条属性是否必填，取值为0或1，默认取值1（必填）。目前本字段是非功能性字段，仅起到描述作用。(字典值link_product_isRequired：0非必填 1必填)	t	f	11	Integer	required		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.095	1	2024-07-10 10:33:24.095	1
241	15	unit	VARCHAR	指示单位。支持长度不超过50。\t取值根据参数确定，如：\t•温度单位：“C”或“K”\t•百分比单位：“%”\t•压强单位：“Pa”或“kPa”	t	f	13	String	unit		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.126	1	2024-07-10 10:33:24.126	1
242	15	create_by	VARCHAR	创建者	t	f	14	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.154	1	2024-07-10 10:33:24.154	1
243	15	create_time	TIMESTAMP	创建时间	t	f	15	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:24.171	1	2024-07-10 10:33:24.171	1
244	15	update_by	VARCHAR	更新者	t	f	16	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.18	1	2024-07-10 10:33:24.18	1
245	15	update_time	TIMESTAMP	更新时间	t	f	17	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:24.193	1	2024-07-10 10:33:24.193	1
246	15	template_code	VARCHAR	模版code	t	f	18	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:24.203	1	2024-07-10 10:33:24.203	1
247	15	pid	VARCHAR	产品唯一标识	t	f	19	String	pid		9569	t	t	t	=	t	input	1	2024-07-10 10:33:24.264	1	2024-07-10 10:33:24.264	1
248	15	tenant_id	BIGINT	租户ID	t	f	20	Long	tenantId		10008	f	f	f	=	f	input	1	2024-07-10 10:33:24.318	1	2024-07-10 10:33:24.318	1
180	12	id	BIGINT	id	f	t	1	Long	id		2458	f	t	f	=	t	input	1	2024-07-10 10:33:18.663	1	2024-07-10 10:33:18.663	1
181	12	commands_id	BIGINT	命令ID	f	f	2	Long	commandsId		13244	t	t	t	=	t	input	1	2024-07-10 10:33:18.677	1	2024-07-10 10:33:18.677	1
182	12	service_id	BIGINT	服务ID	t	f	3	Long	serviceId		19700	t	t	t	=	t	input	1	2024-07-10 10:33:18.726	1	2024-07-10 10:33:18.726	1
183	12	datatype	VARCHAR	指示数据类型。取值范围：string、int、decimal\t	f	f	4	String	datatype		2	t	t	t	=	t	select	1	2024-07-10 10:33:18.741	1	2024-07-10 10:33:18.741	1
184	12	enumlist	VARCHAR	指示枚举值。\t如开关状态status可有如下取值\t"enumList" : ["OPEN","CLOSE"]\t目前本字段是非功能性字段，仅起到描述作用。建议准确定义。\t	t	f	5	String	enumlist		\N	t	t	t	=	t	input	1	2024-07-10 10:33:18.752	1	2024-07-10 10:33:18.752	1
185	12	max	VARCHAR	指示最大值。\t仅当dataType为int、decimal时生效，逻辑小于等于。	t	f	6	String	max		\N	t	t	t	=	t	input	1	2024-07-10 10:33:18.769	1	2024-07-10 10:33:18.769	1
186	12	maxlength	VARCHAR	指示字符串长度。\t仅当dataType为string时生效。	t	f	7	String	maxlength		\N	t	t	t	=	t	input	1	2024-07-10 10:33:18.777	1	2024-07-10 10:33:18.777	1
187	12	min	VARCHAR	指示最小值。\t仅当dataType为int、decimal时生效，逻辑大于等于。	t	f	8	String	min		\N	t	t	t	=	t	input	1	2024-07-10 10:33:18.787	1	2024-07-10 10:33:18.787	1
188	12	parameter_description	VARCHAR	命令中参数的描述，不影响实际功能，可配置为空字符串""。	t	f	9	String	parameterDescription		你说的对	t	t	t	=	t	editor	1	2024-07-10 10:33:18.853	1	2024-07-10 10:33:18.853	1
196	12	update_time	TIMESTAMP	更新时间	f	f	17	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:19.004	1	2024-07-10 10:33:19.004	1
197	12	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		24633	f	f	f	=	f	input	1	2024-07-10 10:33:19.024	1	2024-07-10 10:33:19.024	1
171	11	parameter_name	VARCHAR	命令中参数的名字。	t	f	10	String	parameterName		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:33:17.145	1	2024-07-10 10:33:17.145	1
172	11	required	VARCHAR	指示本条属性是否必填，取值为0或1，默认取值1（必填）。\t目前本字段是非功能性字段，仅起到描述作用。	f	f	11	String	required		\N	t	t	t	=	t	input	1	2024-07-10 10:33:17.16	1	2024-07-10 10:33:17.16	1
173	11	step	VARCHAR	指示步长。	t	f	12	String	step		\N	t	t	t	=	t	input	1	2024-07-10 10:33:17.196	1	2024-07-10 10:33:17.196	1
174	11	unit	VARCHAR	指示单位。\t取值根据参数确定，如：\t•温度单位：“C”或“K”\t•百分比单位：“%”\t•压强单位：“Pa”或“kPa”\t	t	f	13	String	unit		\N	t	t	t	=	t	input	1	2024-07-10 10:33:17.206	1	2024-07-10 10:33:17.206	1
175	11	create_by	VARCHAR	创建者	t	f	14	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:17.217	1	2024-07-10 10:33:17.217	1
176	11	create_time	TIMESTAMP	创建时间	f	f	15	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:17.237	1	2024-07-10 10:33:17.237	1
177	11	update_by	VARCHAR	更新者	t	f	16	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:17.289	1	2024-07-10 10:33:17.289	1
178	11	update_time	TIMESTAMP	更新时间	f	f	17	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:17.332	1	2024-07-10 10:33:17.332	1
179	11	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		15225	f	f	f	=	f	input	1	2024-07-10 10:33:17.346	1	2024-07-10 10:33:17.346	1
127	9	id	BIGINT	id	f	t	1	Long	id		12926	f	t	f	=	t	input	1	2024-07-10 10:33:12.692	1	2024-07-10 10:33:12.692	1
128	9	template_code	VARCHAR	模板code	t	f	2	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:12.707	1	2024-07-10 10:33:12.707	1
129	9	name	VARCHAR	产品名称	f	f	3	String	name		李四	t	t	t	LIKE	t	input	1	2024-07-10 10:33:12.727	1	2024-07-10 10:33:12.727	1
130	9	pid	VARCHAR	产品唯一标识	f	f	4	String	pid		2009	t	t	t	=	t	input	1	2024-07-10 10:33:12.742	1	2024-07-10 10:33:12.742	1
84	6	package_type	SMALLINT	升级包类型(0:软件包、1:固件包)	f	f	4	Short	packageType		2	t	t	t	=	t	select	1	2024-07-10 10:33:07.563	1	2024-07-10 10:33:07.563	1
131	9	manufacturer_name	VARCHAR	厂商名称 :支持中文、英文大小写、数字、下划线和中划线	f	f	5	String	manufacturerName		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:33:12.799	1	2024-07-10 10:33:12.799	1
132	9	model	VARCHAR	产品型号，建议包含字母或数字来保证可扩展性。支持英文大小写、数字、下划线和中划线	f	f	6	String	model		\N	t	t	t	=	t	input	1	2024-07-10 10:33:12.832	1	2024-07-10 10:33:12.832	1
133	9	data_format	VARCHAR	数据格式，默认为JSON无需修改。	f	f	7	String	dataFormat		\N	t	t	t	=	t	input	1	2024-07-10 10:33:12.844	1	2024-07-10 10:33:12.844	1
134	9	protocol_type	VARCHAR	设备接入平台的协议类型，默认为MQTT无需修改。	f	f	8	String	protocolType		2	t	t	t	=	t	select	1	2024-07-10 10:33:12.857	1	2024-07-10 10:33:12.857	1
135	9	enabled_status	SMALLINT	状态(字典值：0启用  1停用)	f	f	9	Short	enabledStatus		2	t	t	t	=	t	radio	1	2024-07-10 10:33:12.888	1	2024-07-10 10:33:12.888	1
217	14	maxlength	VARCHAR	指示字符串长度。\t仅当dataType为string时生效。	t	f	7	String	maxlength		\N	t	t	t	=	t	input	1	2024-07-10 10:33:22.411	1	2024-07-10 10:33:22.411	1
218	14	min	VARCHAR	指示最小值。\t仅当dataType为int、decimal时生效，逻辑大于等于。	t	f	8	String	min		\N	t	t	t	=	t	input	1	2024-07-10 10:33:22.435	1	2024-07-10 10:33:22.435	1
219	14	parameter_description	VARCHAR	命令中参数的描述，不影响实际功能，可配置为空字符串""。	t	f	9	String	parameterDescription		你猜	t	t	t	=	t	editor	1	2024-07-10 10:33:22.444	1	2024-07-10 10:33:22.444	1
220	14	parameter_name	VARCHAR	命令中参数的名字。	t	f	10	String	parameterName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 10:33:22.47	1	2024-07-10 10:33:22.47	1
221	14	required	VARCHAR	指示本条属性是否必填，取值为0或1，默认取值1（必填）。\t目前本字段是非功能性字段，仅起到描述作用。	f	f	11	String	required		\N	t	t	t	=	t	input	1	2024-07-10 10:33:22.547	1	2024-07-10 10:33:22.547	1
222	14	step	VARCHAR	指示步长。	t	f	12	String	step		\N	t	t	t	=	t	input	1	2024-07-10 10:33:22.661	1	2024-07-10 10:33:22.661	1
223	14	unit	VARCHAR	指示单位。\t取值根据参数确定，如：\t•温度单位：“C”或“K”\t•百分比单位：“%”\t•压强单位：“Pa”或“kPa”	t	f	13	String	unit		\N	t	t	t	=	t	input	1	2024-07-10 10:33:22.67	1	2024-07-10 10:33:22.67	1
224	14	create_by	VARCHAR	创建者	t	f	14	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:22.689	1	2024-07-10 10:33:22.689	1
225	14	create_time	TIMESTAMP	创建时间	t	f	15	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:22.718	1	2024-07-10 10:33:22.718	1
226	14	update_by	VARCHAR	更新者	t	f	16	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:22.731	1	2024-07-10 10:33:22.731	1
227	14	update_time	TIMESTAMP	更新时间	t	f	17	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:22.741	1	2024-07-10 10:33:22.741	1
228	14	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		20529	f	f	f	=	f	input	1	2024-07-10 10:33:22.75	1	2024-07-10 10:33:22.75	1
772	45	id	BIGINT	id	f	t	1	Long	id		25168	f	t	f	=	t	input	1	2024-07-10 18:03:37.488	1	2024-07-10 18:41:06.363	0
773	45	property_name	VARCHAR	功能名称	f	f	2	String	propertyName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 18:03:37.559	1	2024-07-10 18:41:06.376	0
774	45	property_code	VARCHAR	属性code	f	f	3	String	propertyCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.57	1	2024-07-10 18:41:06.386	0
775	45	datatype	VARCHAR	指示数据类型：取值范围：string、int、decimal（float和double都可以使用此类型）、DateTime、jsonObject上报数据时，复杂类型数据格式如下：\t•DateTime:yyyyMMdd’T’HHmmss’Z’如:20151212T121212Z•jsonObject：自定义json结构体，平台不理解只透传	f	f	4	String	datatype		2	t	t	t	=	t	select	1	2024-07-10 18:03:37.581	1	2024-07-10 18:41:06.401	0
770	44	update_time	TIMESTAMP	更新时间	t	f	17	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:37.013	1	2024-07-10 18:41:16.189	0
771	44	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		9996	f	f	f	=	f	input	1	2024-07-10 18:03:37.03	1	2024-07-10 18:41:16.199	0
136	9	remark	VARCHAR	产品描述	t	f	10	String	remark		你猜	t	t	t	=	t	input	1	2024-07-10 10:33:12.926	1	2024-07-10 10:33:12.926	1
137	9	create_by	VARCHAR	创建者	t	f	11	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.153	1	2024-07-10 10:33:13.153	1
138	9	create_time	TIMESTAMP	创建时间	t	f	12	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 10:33:13.188	1	2024-07-10 10:33:13.188	1
139	9	update_by	VARCHAR	更新者	t	f	13	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.199	1	2024-07-10 10:33:13.199	1
140	9	update_time	TIMESTAMP	更新时间	t	f	14	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 10:33:13.208	1	2024-07-10 10:33:13.208	1
141	9	auth_mode	VARCHAR	认证方式	t	f	15	String	authMode		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.243	1	2024-07-10 10:33:13.243	1
142	9	user_name	VARCHAR	用户名	t	f	16	String	userName		王五	t	t	t	LIKE	t	input	1	2024-07-10 10:33:13.414	1	2024-07-10 10:33:13.414	1
143	9	password	VARCHAR	密码	t	f	17	String	password		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.453	1	2024-07-10 10:33:13.453	1
144	9	connector	VARCHAR	连接实例	t	f	18	String	connector		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.534	1	2024-07-10 10:33:13.534	1
145	9	sign_key	VARCHAR	签名密钥	t	f	19	String	signKey		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.608	1	2024-07-10 10:33:13.608	1
146	9	encrypt_method	INTEGER	协议加密方式 0：不加密 1：SM4加密 2：AES加密	t	f	20	Integer	encryptMethod		\N	t	t	t	=	t	input	1	2024-07-10 10:33:13.617	1	2024-07-10 10:33:13.617	1
98	7	task_id	BIGINT	任务ID，关联ota_upgrade_tasks表	f	f	2	Long	taskId		10252	t	t	t	=	t	input	1	2024-07-10 10:33:09.041	1	2024-07-10 10:33:09.041	1
99	7	did	VARCHAR	设备标识	f	f	3	String	did		26548	t	t	t	=	t	input	1	2024-07-10 10:33:09.05	1	2024-07-10 10:33:09.05	1
108	7	log_details	VARCHAR	升级过程日志	t	f	12	String	logDetails		\N	t	t	t	=	t	input	1	2024-07-10 10:33:09.2	1	2024-07-10 10:33:09.2	1
109	7	remark	VARCHAR	描述	t	f	13	String	remark		你说的对	t	t	t	=	t	input	1	2024-07-10 10:33:09.209	1	2024-07-10 10:33:09.209	1
110	7	created_time	TIMESTAMP	记录创建时间	f	f	14	LocalDateTime	createdTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:09.221	1	2024-07-10 10:33:09.221	1
111	7	created_by	BIGINT	创建人	t	f	15	Long	createdBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:09.261	1	2024-07-10 10:33:09.261	1
112	7	updated_by	BIGINT	更新人	t	f	16	Long	updatedBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:09.326	1	2024-07-10 10:33:09.326	1
113	7	updated_time	TIMESTAMP	更新时间	f	f	17	LocalDateTime	updatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:09.437	1	2024-07-10 10:33:09.437	1
114	7	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		25458	f	f	f	=	f	input	1	2024-07-10 10:33:09.518	1	2024-07-10 10:33:09.518	1
85	6	product_identification	VARCHAR	产品标识	f	f	5	String	productIdentification		\N	t	t	t	=	t	input	1	2024-07-10 10:33:07.616	1	2024-07-10 10:33:07.616	1
86	6	version	VARCHAR	升级包版本号	f	f	6	String	version		\N	t	t	t	=	t	input	1	2024-07-10 10:33:07.633	1	2024-07-10 10:33:07.633	1
87	6	file_location	VARCHAR	升级包的位置	f	f	7	String	fileLocation		\N	t	t	t	=	t	input	1	2024-07-10 10:33:07.661	1	2024-07-10 10:33:07.661	1
88	6	status	SMALLINT	状态	f	f	8	Short	status		1	t	t	t	=	t	radio	1	2024-07-10 10:33:07.671	1	2024-07-10 10:33:07.671	1
89	6	description	VARCHAR	升级包功能描述	t	f	9	String	description		随便	t	t	t	=	t	editor	1	2024-07-10 10:33:07.684	1	2024-07-10 10:33:07.684	1
90	6	custom_info	VARCHAR	自定义信息	t	f	10	String	customInfo		\N	t	t	t	=	t	input	1	2024-07-10 10:33:07.709	1	2024-07-10 10:33:07.709	1
91	6	remark	VARCHAR	描述	t	f	11	String	remark		随便	t	t	t	=	t	input	1	2024-07-10 10:33:07.779	1	2024-07-10 10:33:07.779	1
92	6	created_by	BIGINT	创建人	t	f	12	Long	createdBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:07.79	1	2024-07-10 10:33:07.79	1
93	6	created_time	TIMESTAMP	创建时间	f	f	13	LocalDateTime	createdTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:07.825	1	2024-07-10 10:33:07.825	1
94	6	updated_by	BIGINT	更新人	t	f	14	Long	updatedBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:07.89	1	2024-07-10 10:33:07.89	1
95	6	updated_time	TIMESTAMP	更新时间	f	f	15	LocalDateTime	updatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:07.9	1	2024-07-10 10:33:07.9	1
96	6	tenant_id	BIGINT	租户ID	t	f	16	Long	tenantId		22198	f	f	f	=	f	input	1	2024-07-10 10:33:07.921	1	2024-07-10 10:33:07.921	1
62	4	file_size	BIGINT	文件大小	t	f	6	Long	fileSize		\N	t	t	t	=	t	input	1	2024-07-10 10:33:03.869	1	2024-07-10 10:33:03.869	1
63	4	remark	VARCHAR	备注	t	f	7	String	remark		你说的对	t	t	t	=	t	input	1	2024-07-10 10:33:03.88	1	2024-07-10 10:33:03.88	1
64	4	status	SMALLINT	状态[0:成功, 1:未开始, 2:上传中, 3:失败]	t	f	8	Short	status		2	t	t	t	=	t	radio	1	2024-07-10 10:33:03.89	1	2024-07-10 10:33:03.89	1
65	4	created_by	VARCHAR	创建者	t	f	9	String	createdBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:03.899	1	2024-07-10 10:33:03.899	1
66	4	created_time	TIMESTAMP	创建时间	t	f	10	LocalDateTime	createdTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:03.938	1	2024-07-10 10:33:03.938	1
67	4	updated_by	VARCHAR	更新者	t	f	11	String	updatedBy		\N	t	t	t	=	t	input	1	2024-07-10 10:33:03.977	1	2024-07-10 10:33:03.977	1
68	4	updated_time	TIMESTAMP	更新时间	t	f	12	LocalDateTime	updatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 10:33:03.99	1	2024-07-10 10:33:03.99	1
69	4	tenant_id	BIGINT	租户ID	t	f	13	Long	tenantId		15826	f	f	f	=	f	input	1	2024-07-10 10:33:04.025	1	2024-07-10 10:33:04.025	1
798	46	description	VARCHAR	服务的描述信息:文本描述，不影响实际功能，可配置为空字符串""。\t	t	f	7	String	description		随便	t	t	t	=	t	editor	1	2024-07-10 18:03:38.6	1	2024-07-10 18:40:55.866	0
811	47	create_time	TIMESTAMP	创建时间	f	f	8	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:39.535	1	2024-07-10 18:40:46.345	0
812	47	update_by	VARCHAR	更新者	t	f	9	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:39.569	1	2024-07-10 18:40:46.356	0
813	47	update_time	TIMESTAMP	更新时间	f	f	10	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:39.59	1	2024-07-10 18:40:46.366	0
814	47	tenant_id	BIGINT	租户ID	t	f	11	Long	tenantId		13638	f	f	f	=	f	input	1	2024-07-10 18:03:39.618	1	2024-07-10 18:40:46.378	0
815	48	id	BIGINT	id	f	t	1	Long	id		4261	f	t	f	=	t	input	1	2024-07-10 18:03:40.493	1	2024-07-10 18:40:34.85	0
834	49	content	VARCHAR	内容	t	f	11	String	content		\N	t	t	t	=	t	editor	1	2024-07-10 18:03:41.412	1	2024-07-10 18:40:26.59	0
835	49	status	VARCHAR	状态(字典值：0启用  1停用)	f	f	12	String	status		2	t	t	t	=	t	radio	1	2024-07-10 18:03:41.471	1	2024-07-10 18:40:26.67	0
836	49	create_by	VARCHAR	创建者	t	f	13	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:41.529	1	2024-07-10 18:40:26.691	0
816	48	name	VARCHAR	名称	f	f	2	String	name		张三	t	t	t	LIKE	t	input	1	2024-07-10 18:03:40.599	1	2024-07-10 18:40:34.873	0
824	49	id	BIGINT	id	f	t	1	Long	id		13300	f	t	f	=	t	input	1	2024-07-10 18:03:41.267	1	2024-07-10 18:40:26.4	0
825	49	app_id	VARCHAR	应用ID	f	f	2	String	appId		19445	t	t	t	=	t	input	1	2024-07-10 18:03:41.293	1	2024-07-10 18:40:26.414	0
826	49	pid	VARCHAR	产品标识	f	f	3	String	pid		27442	t	t	t	=	t	input	1	2024-07-10 18:03:41.302	1	2024-07-10 18:40:26.424	0
827	49	protocol_name	VARCHAR	协议名称	t	f	4	String	protocolName		张三	t	t	t	LIKE	t	input	1	2024-07-10 18:03:41.311	1	2024-07-10 18:40:26.438	0
828	49	protocol_code	VARCHAR	协议标识	t	f	5	String	protocolCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:41.321	1	2024-07-10 18:40:26.451	0
829	49	protocol_version	VARCHAR	协议版本	t	f	6	String	protocolVersion		\N	t	t	t	=	t	input	1	2024-07-10 18:03:41.331	1	2024-07-10 18:40:26.462	0
830	49	protocol_type	VARCHAR	协议类型 ：mqtt || coap || modbus || http	t	f	7	String	protocolType		1	t	t	t	=	t	select	1	2024-07-10 18:03:41.348	1	2024-07-10 18:40:26.475	0
831	49	protocol_voice	VARCHAR	协议语言	t	f	8	String	protocolVoice		\N	t	t	t	=	t	input	1	2024-07-10 18:03:41.369	1	2024-07-10 18:40:26.486	0
832	49	class_name	VARCHAR	类名	t	f	9	String	className		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 18:03:41.388	1	2024-07-10 18:40:26.497	0
817	48	sort	BIGINT	排序序号	t	f	3	Long	sort		\N	t	t	t	=	t	input	1	2024-07-10 18:03:40.609	1	2024-07-10 18:40:34.884	0
818	48	parent_id	VARCHAR	父级ID	t	f	4	String	parentId		22607	t	t	t	=	t	input	1	2024-07-10 18:03:40.619	1	2024-07-10 18:40:34.895	0
819	48	create_by	VARCHAR	创建者	t	f	5	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:40.631	1	2024-07-10 18:40:34.908	0
820	48	create_time	TIMESTAMP	创建时间	t	f	6	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:40.644	1	2024-07-10 18:40:34.919	0
821	48	update_by	VARCHAR	更新者	t	f	7	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:40.652	1	2024-07-10 18:40:34.998	0
822	48	update_time	TIMESTAMP	更新时间	t	f	8	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:40.661	1	2024-07-10 18:40:35.098	0
823	48	tenant_id	BIGINT	租户ID	t	f	9	Long	tenantId		4796	f	f	f	=	f	input	1	2024-07-10 18:03:40.677	1	2024-07-10 18:40:35.119	0
807	47	template_name	VARCHAR	产品模板名称:自定义，支持中文、英文大小写、数字、下划线和中划线	f	f	4	String	templateName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 18:03:39.402	1	2024-07-10 18:40:46.291	0
842	50	id	BIGINT	主键	f	t	1	Long	id		32529	f	t	f	=	t	input	1	2024-07-10 18:03:42.292	1	2025-08-13 13:37:21.852	0
808	47	status	VARCHAR	状态(字典值：启用  停用)	f	f	5	String	status		1	t	t	t	=	t	radio	1	2024-07-10 18:03:39.428	1	2024-07-10 18:40:46.305	0
809	47	remark	VARCHAR	产品模型模板描述	t	f	6	String	remark		随便	t	t	t	=	t	input	1	2024-07-10 18:03:39.456	1	2024-07-10 18:40:46.315	0
810	47	create_by	VARCHAR	创建者	t	f	7	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:39.478	1	2024-07-10 18:40:46.329	0
833	49	file_path	VARCHAR	文件地址	t	f	10	String	filePath		\N	t	t	t	=	t	input	1	2024-07-10 18:03:41.398	1	2024-07-10 18:40:26.511	0
878	53	id	BIGINT	主键	f	t	1	Long	id		935	f	t	f	=	t	input	1	2024-07-10 18:03:45.742	1	2024-07-10 18:40:17.375	0
879	53	rule_id	BIGINT	规则ID	f	f	2	Long	ruleId		19677	t	t	t	=	t	input	1	2024-07-10 18:03:45.762	1	2024-07-10 18:40:17.385	0
880	53	condition_type	SMALLINT	条件类型(0:匹配设备触发、1:指定设备触发、2:按策略定时触发)	f	f	3	Short	conditionType		2	t	t	t	=	t	select	1	2024-07-10 18:03:45.771	1	2024-07-10 18:40:17.403	0
881	53	did	VARCHAR	设备标识(匹配设备设备类型存储一个产品下所有的设备标识逗号分隔，指定设备触发存储指定的设备标识)	t	f	4	String	did		12529	t	t	t	=	t	input	1	2024-07-10 18:03:45.78	1	2024-07-10 18:40:17.422	0
882	53	pid	VARCHAR	产品标识	t	f	5	String	pid		7829	t	t	t	=	t	input	1	2024-07-10 18:03:45.797	1	2024-07-10 18:40:17.432	0
883	53	service_id	BIGINT	服务ID	t	f	6	Long	serviceId		23714	t	t	t	=	t	input	1	2024-07-10 18:03:45.808	1	2024-07-10 18:40:17.449	0
884	53	properties_id	BIGINT	属性ID	t	f	7	Long	propertiesId		18963	t	t	t	=	t	input	1	2024-07-10 18:03:45.82	1	2024-07-10 18:40:17.465	0
885	53	comparison_mode	VARCHAR	比较模式\t<\t<=\t>\t>=\t==\t!=\tin\tbetween	t	f	8	String	comparisonMode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:45.834	1	2024-07-10 18:40:17.476	0
887	53	create_by	VARCHAR	创建人	t	f	10	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:46.081	1	2024-07-10 18:40:17.509	0
867	52	id	BIGINT	主键	f	t	1	Long	id		3790	f	t	f	=	t	input	1	2024-07-10 18:03:44.726	1	2024-07-10 18:40:10.093	0
868	52	alarm_time	TIMESTAMP	告警时间	t	f	2	LocalDateTime	alarmTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:44.747	1	2024-07-10 18:40:10.105	0
869	52	alarm_name	VARCHAR	告警名称	t	f	3	String	alarmName		李四	t	t	t	LIKE	t	input	1	2024-07-10 18:03:44.764	1	2024-07-10 18:40:10.115	0
870	52	alarm_level	INTEGER	告警级别	t	f	4	Integer	alarmLevel		\N	t	t	t	=	t	input	1	2024-07-10 18:03:44.788	1	2024-07-10 18:40:10.126	0
871	52	alarm_describe	VARCHAR	告警描述	t	f	5	String	alarmDescribe		\N	t	t	t	=	t	input	1	2024-07-10 18:03:44.798	1	2024-07-10 18:40:10.137	0
872	52	processing_result	INTEGER	处理结果 0 未处理 1已处理	t	f	6	Integer	processingResult		\N	t	t	t	=	t	input	1	2024-07-10 18:03:44.876	1	2024-07-10 18:40:10.148	0
873	52	processing_opinions	VARCHAR	处理意见	t	f	7	String	processingOpinions		\N	t	t	t	=	t	input	1	2024-07-10 18:03:44.962	1	2024-07-10 18:40:10.158	0
874	52	alarm_content	VARCHAR	告警内容	t	f	8	String	alarmContent		\N	t	t	t	=	t	editor	1	2024-07-10 18:03:44.98	1	2024-07-10 18:40:10.169	0
875	52	processing_people	VARCHAR	处理人	t	f	9	String	processingPeople		\N	t	t	t	=	t	input	1	2024-07-10 18:03:44.99	1	2024-07-10 18:40:10.189	0
888	53	create_time	TIMESTAMP	创建时间	f	f	11	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:46.12	1	2024-07-10 18:40:17.567	0
857	51	rule_alarm_name	VARCHAR	告警规则名称	t	f	3	String	ruleAlarmName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 18:03:43.907	1	2024-07-10 18:40:02.713	0
858	51	rule_alarm_status	INTEGER	告警状态0 未启动  1运行中	t	f	4	Integer	ruleAlarmStatus		1	t	t	t	=	t	radio	1	2024-07-10 18:03:43.939	1	2024-07-10 18:40:02.724	0
859	51	rule_alarm_remark	VARCHAR	告警规则描述	t	f	5	String	ruleAlarmRemark		你猜	t	t	t	=	t	input	1	2024-07-10 18:03:43.986	1	2024-07-10 18:40:02.734	0
860	51	rule_level	INTEGER	告警级别	t	f	6	Integer	ruleLevel		\N	t	t	t	=	t	input	1	2024-07-10 18:03:43.996	1	2024-07-10 18:40:02.753	0
861	51	notice_type	INTEGER	通知方式	t	f	7	Integer	noticeType		1	t	t	t	=	t	select	1	2024-07-10 18:03:44.01	1	2024-07-10 18:40:02.764	0
862	51	create_by	VARCHAR	创建人	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:44.023	1	2024-07-10 18:40:02.774	0
863	51	create_time	TIMESTAMP	创建时间	f	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:44.179	1	2024-07-10 18:40:02.788	0
864	51	update_by	VARCHAR	更新人	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:44.188	1	2024-07-10 18:40:02.808	0
865	51	update_time	TIMESTAMP	更新时间	f	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:44.197	1	2024-07-10 18:40:02.831	0
866	51	tenant_id	BIGINT	租户ID	t	f	12	Long	tenantId		23714	f	f	f	=	f	input	1	2024-07-10 18:03:44.212	1	2024-07-10 18:40:02.843	0
876	52	processing_time	TIMESTAMP	处理时间	t	f	10	LocalDateTime	processingTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:44.999	1	2024-07-10 18:40:10.2	0
805	47	app_id	VARCHAR	应用ID	f	f	2	String	appId		25237	t	t	t	=	t	input	1	2024-07-10 18:03:39.375	1	2024-07-10 18:40:46.266	0
806	47	template_code	VARCHAR	产品模版标识	f	f	3	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:39.385	1	2024-07-10 18:40:46.278	0
792	46	id	BIGINT	服务id	f	t	1	Long	id		17125	f	t	f	=	t	input	1	2024-07-10 18:03:38.389	1	2024-07-10 18:40:55.762	0
793	46	service_code	VARCHAR	服务编码:支持英文大小写、数字、下划线和中划线	f	f	2	String	serviceCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:38.4	1	2024-07-10 18:40:55.777	0
794	46	service_name	VARCHAR	服务名称	f	f	3	String	serviceName		张三	t	t	t	LIKE	t	input	1	2024-07-10 18:03:38.428	1	2024-07-10 18:40:55.788	0
855	51	id	BIGINT	规则告警ID	f	t	1	Long	id		30719	f	t	f	=	t	input	1	2024-07-10 18:03:43.876	1	2024-07-10 18:40:02.689	0
856	51	rule_id	BIGINT	规则ID	t	f	2	Long	ruleId		15621	t	t	t	=	t	input	1	2024-07-10 18:03:43.89	1	2024-07-10 18:40:02.7	0
886	53	comparison_value	VARCHAR	比较值\t\tbetween类型传值例子  [10,15] 必须是两位，且数字不能重复\t判断数据是否处于一个离散的取值范围内，例如输入[1,2,3,4]，取值范围是1、2、3、4四个值，如果比较值类型为float(double)，两个float（double）型数值相差在0.000001范围内即为相等	t	f	9	String	comparisonValue		\N	t	t	t	=	t	input	1	2024-07-10 18:03:45.919	1	2024-07-10 18:40:17.498	0
889	53	update_by	VARCHAR	更新人	t	f	12	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:46.13	1	2024-07-10 18:40:17.601	0
890	53	update_time	TIMESTAMP	更新时间	f	f	13	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:46.138	1	2024-07-10 18:40:17.631	0
891	53	tenant_id	BIGINT	租户ID	t	f	14	Long	tenantId		332	f	f	f	=	f	input	1	2024-07-10 18:03:46.147	1	2024-07-10 18:40:17.693	0
837	49	create_time	TIMESTAMP	创建时间	f	f	14	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:41.582	1	2024-07-10 18:40:26.701	0
838	49	update_by	VARCHAR	更新者	t	f	15	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:41.599	1	2024-07-10 18:40:26.717	0
839	49	update_time	TIMESTAMP	更新时间	f	f	16	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:41.61	1	2024-07-10 18:40:26.728	0
840	49	remark	VARCHAR	备注	t	f	17	String	remark		你猜	t	t	t	=	t	input	1	2024-07-10 18:03:41.621	1	2024-07-10 18:40:26.739	0
841	49	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		4567	f	f	f	=	f	input	1	2024-07-10 18:03:41.631	1	2024-07-10 18:40:26.75	0
754	44	id	BIGINT	id	f	t	1	Long	id		10003	f	t	f	=	t	input	1	2024-07-10 18:03:36.376	1	2024-07-10 18:41:15.84	0
755	44	event_id	BIGINT	事件id	f	f	2	Long	eventId		12881	t	t	t	=	t	input	1	2024-07-10 18:03:36.387	1	2024-07-10 18:41:15.85	0
756	44	service_id	BIGINT	服务ID	t	f	3	Long	serviceId		9444	t	t	t	=	t	input	1	2024-07-10 18:03:36.397	1	2024-07-10 18:41:15.861	0
757	44	datatype	VARCHAR	指示数据类型。取值范围：string、int、decimal	f	f	4	String	datatype		1	t	t	t	=	t	select	1	2024-07-10 18:03:36.406	1	2024-07-10 18:41:15.878	0
758	44	enumlist	VARCHAR	指示枚举值。\t如开关状态status可有如下取值\t"enumList" : ["OPEN","CLOSE"]\t目前本字段是非功能性字段，仅起到描述作用。建议准确定义。	t	f	5	String	enumlist		\N	t	t	t	=	t	input	1	2024-07-10 18:03:36.511	1	2024-07-10 18:41:15.888	0
748	43	description	VARCHAR	描述	t	f	8	String	description		你说的对	t	t	t	=	t	editor	1	2024-07-10 18:03:35.542	1	2024-07-10 18:41:25.508	0
749	43	create_by	VARCHAR	创建者	t	f	9	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:35.637	1	2024-07-10 18:41:25.539	0
750	43	create_time	TIMESTAMP	创建时间	t	f	10	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:35.65	1	2024-07-10 18:41:25.611	0
751	43	update_by	VARCHAR	更新者	t	f	11	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:35.661	1	2024-07-10 18:41:25.684	0
752	43	update_time	TIMESTAMP	更新时间	t	f	12	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:35.67	1	2024-07-10 18:41:25.826	0
753	43	tenant_id	BIGINT	租户ID	t	f	13	Long	tenantId		2196	f	f	f	=	f	input	1	2024-07-10 18:03:35.68	1	2024-07-10 18:41:25.837	0
723	42	id	BIGINT	id	f	t	1	Long	id		29340	f	t	f	=	t	input	1	2024-07-10 18:03:34.242	1	2024-07-10 18:41:33.084	0
778	45	max	VARCHAR	指示最大值。支持长度不超过50的数字。仅当dataType为int、decimal时生效，逻辑小于等于。	t	f	7	String	max		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.618	1	2024-07-10 18:41:06.438	0
779	45	maxlength	BIGINT	指示字符串长度。仅当dataType为string、DateTime时生效。	t	f	8	Long	maxlength		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.629	1	2024-07-10 18:41:06.456	0
780	45	method	VARCHAR	指示访问模式。R:可读；W:可写；E属性值更改时上报数据取值范围：R、RW、RE、RWE	t	f	9	String	method		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.639	1	2024-07-10 18:41:06.467	0
781	45	min	VARCHAR	指示最小值。支持长度不超过50的数字。仅当dataType为int、decimal时生效，逻辑大于等于。	t	f	10	String	min		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.651	1	2024-07-10 18:41:06.479	0
782	45	required	INTEGER	指示本条属性是否必填，取值为0或1，默认取值1（必填）。目前本字段是非功能性字段，仅起到描述作用。(字典值link_product_isRequired：0非必填 1必填)	t	f	11	Integer	required		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.665	1	2024-07-10 18:41:06.489	0
783	45	step	INTEGER	指示步长。	t	f	12	Integer	step		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.684	1	2024-07-10 18:41:06.503	0
784	45	unit	VARCHAR	指示单位。支持长度不超过50。\t取值根据参数确定，如：\t•温度单位：“C”或“K”\t•百分比单位：“%”\t•压强单位：“Pa”或“kPa”	t	f	13	String	unit		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.697	1	2024-07-10 18:41:06.627	0
785	45	create_by	VARCHAR	创建者	t	f	14	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.707	1	2024-07-10 18:41:06.668	0
786	45	create_time	TIMESTAMP	创建时间	t	f	15	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:37.722	1	2024-07-10 18:41:06.69	0
787	45	update_by	VARCHAR	更新者	t	f	16	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.731	1	2024-07-10 18:41:06.707	0
788	45	update_time	TIMESTAMP	更新时间	t	f	17	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:37.74	1	2024-07-10 18:41:06.721	0
789	45	template_code	VARCHAR	模版code	t	f	18	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:37.753	1	2024-07-10 18:41:06.732	0
790	45	pid	VARCHAR	产品唯一标识	t	f	19	String	pid		27152	t	t	t	=	t	input	1	2024-07-10 18:03:37.767	1	2024-07-10 18:41:06.743	0
791	45	tenant_id	BIGINT	租户ID	t	f	20	Long	tenantId		16536	f	f	f	=	f	input	1	2024-07-10 18:03:37.788	1	2024-07-10 18:41:06.757	0
728	42	max	VARCHAR	指示最大值。\t仅当dataType为int、decimal时生效，逻辑小于等于。	t	f	6	String	max		\N	t	t	t	=	t	input	1	2024-07-10 18:03:34.318	1	2024-07-10 18:41:33.368	0
729	42	maxlength	VARCHAR	指示字符串长度。\t仅当dataType为string时生效。	t	f	7	String	maxlength		\N	t	t	t	=	t	input	1	2024-07-10 18:03:34.33	1	2024-07-10 18:41:33.383	0
730	42	min	VARCHAR	指示最小值。\t仅当dataType为int、decimal时生效，逻辑大于等于。	t	f	8	String	min		\N	t	t	t	=	t	input	1	2024-07-10 18:03:34.345	1	2024-07-10 18:41:33.392	0
731	42	parameter_description	VARCHAR	命令中参数的描述，不影响实际功能，可配置为空字符串""。	t	f	9	String	parameterDescription		你猜	t	t	t	=	t	editor	1	2024-07-10 18:03:34.356	1	2024-07-10 18:41:33.404	0
732	42	parameter_name	VARCHAR	命令中参数的名字。	t	f	10	String	parameterName		芋艿	t	t	t	LIKE	t	input	1	2024-07-10 18:03:34.366	1	2024-07-10 18:41:33.419	0
675	39	model	VARCHAR	产品型号，建议包含字母或数字来保证可扩展性。支持英文大小写、数字、下划线和中划线	f	f	6	String	model		\N	t	t	t	=	t	input	1	2024-07-10 18:03:30.653	1	2024-07-10 18:41:58.273	0
676	39	data_format	VARCHAR	数据格式，默认为JSON无需修改。	f	f	7	String	dataFormat		\N	t	t	t	=	t	input	1	2024-07-10 18:03:30.67	1	2024-07-10 18:41:58.284	0
677	39	protocol_type	VARCHAR	设备接入平台的协议类型，默认为MQTT无需修改。	f	f	8	String	protocolType		2	t	t	t	=	t	select	1	2024-07-10 18:03:30.681	1	2024-07-10 18:41:58.294	0
678	39	enabled_status	SMALLINT	状态(字典值：0启用  1停用)	f	f	9	Short	enabledStatus		2	t	t	t	=	t	radio	1	2024-07-10 18:03:30.7	1	2024-07-10 18:41:58.304	0
679	39	remark	VARCHAR	产品描述	t	f	10	String	remark		你猜	t	t	t	=	t	input	1	2024-07-10 18:03:30.719	1	2024-07-10 18:41:58.321	0
680	39	create_by	VARCHAR	创建者	t	f	11	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:30.736	1	2024-07-10 18:41:58.331	0
658	38	id	BIGINT	主键	f	t	1	Long	id		20131	f	t	f	=	t	input	1	2024-07-10 18:03:29.471	1	2024-07-10 18:42:05.95	0
659	38	upgrade_id	BIGINT	升级包ID，关联ota_upgrades表	f	f	2	Long	upgradeId		31869	t	t	t	=	t	input	1	2024-07-10 18:03:29.494	1	2024-07-10 18:42:05.959	0
696	40	id	BIGINT	命令id	f	t	1	Long	id		27929	f	t	f	=	t	input	1	2024-07-10 18:03:31.752	1	2024-07-10 18:41:48.693	0
697	40	service_id	BIGINT	服务ID	f	f	2	Long	serviceId		3336	t	t	t	=	t	input	1	2024-07-10 18:03:31.769	1	2024-07-10 18:41:48.714	0
698	40	name	VARCHAR	指示命令的名字，如门磁的LOCK命令、摄像头的VIDEO_RECORD命令，命令名与参数共同构成一个完整的命令。\t支持英文大小写、数字及下划线，长度[2,50]。\t	f	f	3	String	name		张三	t	t	t	LIKE	t	input	1	2024-07-10 18:03:31.779	1	2024-07-10 18:41:48.726	0
699	40	description	VARCHAR	命令描述。	t	f	4	String	description		你猜	t	t	t	=	t	editor	1	2024-07-10 18:03:31.788	1	2024-07-10 18:41:48.737	0
700	40	create_by	VARCHAR	创建者	t	f	5	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:31.798	1	2024-07-10 18:41:48.754	0
701	40	create_time	TIMESTAMP	创建时间	f	f	6	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:31.853	1	2024-07-10 18:41:48.766	0
708	41	datatype	VARCHAR	指示数据类型。取值范围：string、int、decimal\t	f	f	4	String	datatype		2	t	t	t	=	t	select	1	2024-07-10 18:03:32.936	1	2024-07-10 18:41:41.715	0
709	41	enumlist	VARCHAR	指示枚举值。\t如开关状态status可有如下取值\t"enumList" : ["OPEN","CLOSE"]\t目前本字段是非功能性字段，仅起到描述作用。建议准确定义。\t	t	f	5	String	enumlist		\N	t	t	t	=	t	input	1	2024-07-10 18:03:32.951	1	2024-07-10 18:41:41.729	0
719	41	create_time	TIMESTAMP	创建时间	f	f	15	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:33.258	1	2024-07-10 18:41:41.844	0
720	41	update_by	VARCHAR	更新者	t	f	16	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:33.284	1	2024-07-10 18:41:41.853	0
721	41	update_time	TIMESTAMP	更新时间	f	f	17	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:33.307	1	2024-07-10 18:41:41.862	0
722	41	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		22674	f	f	f	=	f	input	1	2024-07-10 18:03:33.318	1	2024-07-10 18:41:41.872	0
702	40	update_by	VARCHAR	更新者	t	f	7	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:31.881	1	2024-07-10 18:41:48.816	0
703	40	update_time	TIMESTAMP	更新时间	f	f	8	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:31.922	1	2024-07-10 18:41:48.83	0
670	39	id	BIGINT	id	f	t	1	Long	id		18586	f	t	f	=	t	input	1	2024-07-10 18:03:30.46	1	2024-07-10 18:41:58.224	0
671	39	template_code	VARCHAR	模板code	t	f	2	String	templateCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:30.474	1	2024-07-10 18:41:58.234	0
672	39	name	VARCHAR	产品名称	f	f	3	String	name		王五	t	t	t	LIKE	t	input	1	2024-07-10 18:03:30.582	1	2024-07-10 18:41:58.243	0
673	39	pid	VARCHAR	产品唯一标识	f	f	4	String	pid		17483	t	t	t	=	t	input	1	2024-07-10 18:03:30.617	1	2024-07-10 18:41:58.252	0
674	39	manufacturer_name	VARCHAR	厂商名称 :支持中文、英文大小写、数字、下划线和中划线	f	f	5	String	manufacturerName		李四	t	t	t	LIKE	t	input	1	2024-07-10 18:03:30.633	1	2024-07-10 18:41:58.263	0
632	36	description	VARCHAR	升级包功能描述	t	f	9	String	description		你说的对	t	t	t	=	t	editor	1	2024-07-10 18:03:27.389	1	2024-07-10 18:42:24.899	0
633	36	custom_info	VARCHAR	自定义信息	t	f	10	String	customInfo		\N	t	t	t	=	t	input	1	2024-07-10 18:03:27.399	1	2024-07-10 18:42:24.91	0
634	36	remark	VARCHAR	描述	t	f	11	String	remark		随便	t	t	t	=	t	input	1	2024-07-10 18:03:27.409	1	2024-07-10 18:42:24.922	0
635	36	created_by	BIGINT	创建人	t	f	12	Long	createdBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:27.504	1	2024-07-10 18:42:24.949	0
654	37	created_by	BIGINT	创建人	t	f	15	Long	createdBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:29.024	1	2024-07-10 18:42:13.515	0
655	37	updated_by	BIGINT	更新人	t	f	16	Long	updatedBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:29.035	1	2024-07-10 18:42:13.538	0
656	37	updated_time	TIMESTAMP	更新时间	f	f	17	LocalDateTime	updatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:29.045	1	2024-07-10 18:42:13.58	0
657	37	tenant_id	BIGINT	租户ID	t	f	18	Long	tenantId		31310	f	f	f	=	f	input	1	2024-07-10 18:03:29.06	1	2024-07-10 18:42:13.674	0
624	36	id	BIGINT	主键	f	t	1	Long	id		18901	f	t	f	=	t	input	1	2024-07-10 18:03:27.285	1	2024-07-10 18:42:24.759	0
625	36	app_id	VARCHAR	应用ID	f	f	2	String	appId		22624	t	t	t	=	t	input	1	2024-07-10 18:03:27.297	1	2024-07-10 18:42:24.807	0
626	36	package_name	VARCHAR	包名称	f	f	3	String	packageName		赵六	t	t	t	LIKE	t	input	1	2024-07-10 18:03:27.306	1	2024-07-10 18:42:24.82	0
627	36	package_type	SMALLINT	升级包类型(0:软件包、1:固件包)	f	f	4	Short	packageType		2	t	t	t	=	t	select	1	2024-07-10 18:03:27.317	1	2024-07-10 18:42:24.833	0
628	36	product_identification	VARCHAR	产品标识	f	f	5	String	productIdentification		\N	t	t	t	=	t	input	1	2024-07-10 18:03:27.329	1	2024-07-10 18:42:24.854	0
629	36	version	VARCHAR	升级包版本号	f	f	6	String	version		\N	t	t	t	=	t	input	1	2024-07-10 18:03:27.347	1	2024-07-10 18:42:24.865	0
630	36	file_location	VARCHAR	升级包的位置	f	f	7	String	fileLocation		\N	t	t	t	=	t	input	1	2024-07-10 18:03:27.363	1	2024-07-10 18:42:24.875	0
631	36	status	SMALLINT	状态	f	f	8	Short	status		2	t	t	t	=	t	radio	1	2024-07-10 18:03:27.378	1	2024-07-10 18:42:24.887	0
636	36	created_time	TIMESTAMP	创建时间	f	f	13	LocalDateTime	createdTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:27.648	1	2024-07-10 18:42:25.092	0
581	32	longitude	NUMERIC	经度	t	f	24	BigDecimal	longitude		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.186	1	2024-07-10 18:42:59.123	0
582	32	location_name	VARCHAR	设备所在位置	t	f	25	String	locationName		李四	t	t	t	LIKE	t	input	1	2024-07-10 18:03:22.199	1	2024-07-10 18:42:59.134	0
611	34	updated_time	TIMESTAMP	更新时间	t	f	12	LocalDateTime	updatedTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-07-10 18:03:25.448	1	2024-07-10 18:42:41.485	0
612	34	tenant_id	BIGINT	租户ID	t	f	13	Long	tenantId		2051	f	f	f	=	f	input	1	2024-07-10 18:03:25.474	1	2024-07-10 18:42:41.494	0
558	32	id	BIGINT	id	f	t	1	Long	id		11554	f	t	f	=	t	input	1	2024-07-10 18:03:21.652	1	2024-07-10 18:42:58.393	0
559	32	did	VARCHAR	设备唯一标识	f	f	2	String	did		16408	t	t	t	=	t	input	1	2024-07-10 18:03:21.667	1	2024-07-10 18:42:58.402	0
560	32	name	VARCHAR	设备名称	t	f	3	String	name		李四	t	t	t	LIKE	t	input	1	2024-07-10 18:03:21.682	1	2024-07-10 18:42:58.412	0
561	32	description	VARCHAR	设备描述	t	f	4	String	description		随便	t	t	t	=	t	editor	1	2024-07-10 18:03:21.697	1	2024-07-10 18:42:58.45	0
562	32	enabled_status	VARCHAR	设备状态： ENABLE:启用 || DISABLE:禁用	t	f	5	String	enabledStatus		2	t	t	t	=	t	radio	1	2024-07-10 18:03:21.707	1	2024-07-10 18:42:58.46	0
563	32	connect_status	VARCHAR	连接状态 :    OFFLINE:离线 || ONLINE:在线	t	f	6	String	connectStatus		2	t	t	t	=	t	radio	1	2024-07-10 18:03:21.733	1	2024-07-10 18:42:58.47	0
564	32	pid	VARCHAR	产品唯一标识	f	f	7	String	pid		13799	t	t	t	=	t	input	1	2024-07-10 18:03:21.817	1	2024-07-10 18:42:58.484	0
565	32	create_by	VARCHAR	创建者	t	f	8	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:21.956	1	2024-07-10 18:42:58.493	0
566	32	create_time	TIMESTAMP	创建时间	t	f	9	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:22.005	1	2024-07-10 18:42:58.505	0
567	32	update_by	VARCHAR	更新者	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.015	1	2024-07-10 18:42:58.514	0
568	32	update_time	TIMESTAMP	更新时间	t	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:22.033	1	2024-07-10 18:42:58.559	0
569	32	remark	VARCHAR	备注	t	f	12	String	remark		随便	t	t	t	=	t	input	1	2024-07-10 18:03:22.044	1	2024-07-10 18:42:58.571	0
570	32	device_version	VARCHAR	设备版本号	t	f	13	String	deviceVersion		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.057	1	2024-07-10 18:42:58.694	0
571	32	device_sn	VARCHAR	设备sn号	f	f	14	String	deviceSn		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.068	1	2024-07-10 18:42:58.846	0
579	32	device_type	VARCHAR	支持以下两种产品类型\t•COMMON：普通产品，需直连设备。\t•GATEWAY：网关产品，可挂载子设备。\t•SUBSET：子设备。	t	f	22	String	deviceType		1	t	t	t	=	t	select	1	2024-07-10 18:03:22.163	1	2024-07-10 18:42:58.969	0
580	32	latitude	NUMERIC	纬度	t	f	23	BigDecimal	latitude		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.174	1	2024-07-10 18:42:59.111	0
583	32	province_code	VARCHAR	省,直辖市编码	t	f	26	String	provinceCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.222	1	2024-07-10 18:42:59.149	0
584	32	city_code	VARCHAR	市编码	t	f	27	String	cityCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.244	1	2024-07-10 18:42:59.16	0
585	32	region_code	VARCHAR	区县	t	f	28	String	regionCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:22.257	1	2024-07-10 18:42:59.169	0
586	32	tenant_id	BIGINT	租户ID	t	f	29	Long	tenantId		23085	f	f	f	=	f	input	1	2024-07-10 18:03:22.274	1	2024-07-10 18:42:59.179	0
892	54	id	BIGINT	主键id	f	t	1	Long	id		21883	f	t	f	=	t	input	1	2024-12-26 14:46:24.754	1	2024-12-26 14:46:24.754	0
893	54	space_name	VARCHAR	空间名称	f	f	2	String	spaceName		赵六	t	t	t	LIKE	t	input	1	2024-12-26 14:46:24.862	1	2024-12-26 14:46:24.862	0
894	54	space_id	VARCHAR	空间编号	f	f	3	String	spaceId		9135	t	t	t	=	t	input	1	2024-12-26 14:46:24.981	1	2024-12-26 14:46:24.981	0
895	54	save_mode	SMALLINT	文件保存模式[0:标准存储,1:归档存储]	f	f	4	Short	saveMode		\N	t	t	t	=	t	input	1	2024-12-26 14:46:25.022	1	2024-12-26 14:46:25.022	0
896	54	save_time	INTEGER	文件保存时间[0:永久保存,>=7(单位:天)]	f	f	5	Integer	saveTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-12-26 14:46:25.056	1	2024-12-26 14:46:25.056	0
897	54	create_by	VARCHAR	创建人	t	f	6	String	createBy		\N	t	t	t	=	t	input	1	2024-12-26 14:46:25.116	1	2024-12-26 14:46:25.116	0
898	54	create_time	TIMESTAMP	创建时间	t	f	7	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-12-26 14:46:25.154	1	2024-12-26 14:46:25.154	0
899	54	tenant_id	BIGINT	租户编号	f	f	8	Long	tenantId		7343	f	f	f	=	f	input	1	2024-12-26 14:46:25.175	1	2024-12-26 14:46:25.175	0
900	54	update_by	VARCHAR	创建人	t	f	9	String	updateBy		\N	t	t	t	=	t	input	1	2024-12-26 14:46:25.2	1	2024-12-26 14:46:25.2	0
901	54	update_time	TIMESTAMP	创建时间	t	f	10	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-12-26 14:46:25.243	1	2024-12-26 14:46:25.243	0
902	55	id	BIGINT	主键id	f	t	1	Long	id		26173	f	t	f	=	t	input	1	2024-12-26 14:46:26.709	1	2024-12-26 14:46:26.709	0
903	55	task_type	VARCHAR	任务类型	t	f	2	String	taskType		1	t	t	t	=	t	select	1	2024-12-26 14:46:26.782	1	2024-12-26 14:46:26.782	0
904	55	space_name	VARCHAR	空间名称	t	f	3	String	spaceName		王五	t	t	t	LIKE	t	input	1	2024-12-26 14:46:26.829	1	2024-12-26 14:46:26.829	0
905	55	space_id	VARCHAR	空间编号	f	f	4	String	spaceId		10099	t	t	t	=	t	input	1	2024-12-26 14:46:26.872	1	2024-12-26 14:46:26.872	0
906	55	channel_id	VARCHAR	通道号	f	f	5	String	channelId		29349	t	t	t	=	t	input	1	2024-12-26 14:46:26.909	1	2024-12-26 14:46:26.909	0
907	55	device_id	VARCHAR	设备序列号	f	f	6	String	deviceId		21839	t	t	t	=	t	input	1	2024-12-26 14:46:26.955	1	2024-12-26 14:46:26.955	0
908	55	create_by	VARCHAR	创建人	t	f	7	String	createBy		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.016	1	2024-12-26 14:46:27.016	0
909	55	create_time	TIMESTAMP	创建时间	t	f	8	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-12-26 14:46:27.052	1	2024-12-26 14:46:27.052	0
910	55	tenant_id	BIGINT	租户编号	f	f	9	Long	tenantId		6369	f	f	f	=	f	input	1	2024-12-26 14:46:27.088	1	2024-12-26 14:46:27.088	0
911	55	update_by	VARCHAR	创建人	t	f	10	String	updateBy		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.121	1	2024-12-26 14:46:27.121	0
912	55	update_time	TIMESTAMP	创建时间	t	f	11	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-12-26 14:46:27.176	1	2024-12-26 14:46:27.176	0
913	55	capture_type	SMALLINT	抓拍类型[0:抽帧,1:抓拍]	f	f	12	Short	captureType		2	t	t	t	=	t	select	1	2024-12-26 14:46:27.214	1	2024-12-26 14:46:27.214	0
914	55	capture_start_time	TIMESTAMP	抓拍开始时间	f	f	13	LocalDateTime	captureStartTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-12-26 14:46:27.247	1	2024-12-26 14:46:27.247	0
915	55	capture_end_time	TIMESTAMP	抓拍结束时间	f	f	14	LocalDateTime	captureEndTime		\N	t	t	t	BETWEEN	t	datetime	1	2024-12-26 14:46:27.29	1	2024-12-26 14:46:27.29	0
1055	62	username	VARCHAR	用户名	t	f	13	String	username		王五	t	t	t	LIKE	t	input	1	2025-03-31 16:12:45.044	1	2025-03-31 16:12:45.044	0
916	55	capture_interval_hour	INTEGER	抓拍间隔(小时)	f	f	15	Integer	captureIntervalHour		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.324	1	2024-12-26 14:46:27.324	0
917	55	capture_interval_minute	INTEGER	抓拍间隔(分钟)	f	f	16	Integer	captureIntervalMinute		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.368	1	2024-12-26 14:46:27.368	0
918	55	capture_interval_second	INTEGER	抓拍间隔(秒)	f	f	17	Integer	captureIntervalSecond		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.4	1	2024-12-26 14:46:27.4	0
919	55	algorithm_enabled	SMALLINT	是否推理[0:否,1:是]	f	f	18	Short	algorithmEnabled		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.421	1	2024-12-26 14:46:27.421	0
920	55	alarm_enabled	SMALLINT	是否告警[0:否,1:是]	f	f	19	Short	alarmEnabled		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.45	1	2024-12-26 14:46:27.45	0
921	55	alarm_type	SMALLINT	告警类型[0:短信告警,1:邮箱告警]	t	f	20	Short	alarmType		2	t	t	t	=	t	select	1	2024-12-26 14:46:27.483	1	2024-12-26 14:46:27.483	0
922	55	phone_number	VARCHAR	告警手机号[多个手机号用英文逗号分割]	t	f	21	String	phoneNumber		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.514	1	2024-12-26 14:46:27.514	0
923	55	email	VARCHAR	告警邮箱号[多个邮箱用英文逗号分割]	t	f	22	String	email		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.551	1	2024-12-26 14:46:27.551	0
924	55	algorithm_type	SMALLINT	算法类型[0:火焰烟雾检测算法,1:人群聚集计数检测算法,2:吸烟检测算法]	t	f	23	Short	algorithmType		2	t	t	t	=	t	select	1	2024-12-26 14:46:27.573	1	2024-12-26 14:46:27.573	0
925	55	video_password	VARCHAR	录像解密密钥	t	f	24	String	videoPassword		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.615	1	2024-12-26 14:46:27.615	0
926	55	auto_filename_enabled	SMALLINT	是否文件自动命名[0:否,1:是]	t	f	25	Short	autoFilenameEnabled		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.65	1	2024-12-26 14:46:27.65	0
927	55	custom_filename_prefix_enabled	SMALLINT	是否自定义文件前缀[0:否,1:是]	t	f	26	Short	customFilenamePrefixEnabled		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.676	1	2024-12-26 14:46:27.676	0
928	55	custom_filename_prefix	VARCHAR	自定义文件前缀	t	f	27	String	customFilenamePrefix		\N	t	t	t	=	t	input	1	2024-12-26 14:46:27.719	1	2024-12-26 14:46:27.719	0
929	56	id	BIGINT	主键id	f	t	1	Long	id		1606	f	t	f	=	t	input	1	2025-02-25 09:34:34.388	1	2025-02-25 09:34:34.388	0
930	56	name	VARCHAR	告警名称	f	f	2	String	name		李四	t	t	t	LIKE	t	input	1	2025-02-25 09:34:34.42	1	2025-02-25 09:34:34.42	0
931	56	alarm_type	VARCHAR	告警类型(吸烟、打架、人行、人脸、安全帽、口罩、汽车、摩托车、电瓶车)	f	f	3	String	alarmType		2	t	t	t	=	t	select	1	2025-02-25 09:34:34.438	1	2025-02-25 09:34:34.438	0
932	56	alarm_image	VARCHAR	告警图片	t	f	4	String	alarmImage		\N	t	t	t	=	t	imageUpload	1	2025-02-25 09:34:34.455	1	2025-02-25 09:34:34.455	0
933	56	alarm_time	VARCHAR	告警时间	f	f	5	String	alarmTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-02-25 09:34:34.478	1	2025-02-25 09:34:34.478	0
934	56	task_num	VARCHAR	任务编号	f	f	6	String	taskNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:34.499	1	2025-02-25 09:34:34.499	0
935	56	model_num	VARCHAR	模型编号	f	f	7	String	modelNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:34.516	1	2025-02-25 09:34:34.516	0
936	56	model_name	VARCHAR	模型名称	f	f	8	String	modelName		BasicLab	t	t	t	LIKE	t	input	1	2025-02-25 09:34:34.532	1	2025-02-25 09:34:34.532	0
937	56	customer_num	VARCHAR	客户编号	f	f	9	String	customerNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:34.551	1	2025-02-25 09:34:34.551	0
938	56	customer_name	VARCHAR	客户名称	t	f	10	String	customerName		赵六	t	t	t	LIKE	t	input	1	2025-02-25 09:34:34.57	1	2025-02-25 09:34:34.57	0
939	56	create_by	VARCHAR	创建人	t	f	11	String	createBy		\N	t	t	t	=	t	input	1	2025-02-25 09:34:34.588	1	2025-02-25 09:34:34.588	0
940	56	create_time	TIMESTAMP	创建时间	t	f	12	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2025-02-25 09:34:34.607	1	2025-02-25 09:34:34.607	0
941	56	tenant_id	BIGINT	租户编号	f	f	13	Long	tenantId		8461	f	f	f	=	f	input	1	2025-02-25 09:34:34.621	1	2025-02-25 09:34:34.621	0
942	56	update_by	VARCHAR	创建人	t	f	14	String	updateBy		\N	t	t	t	=	t	input	1	2025-02-25 09:34:34.634	1	2025-02-25 09:34:34.634	0
943	56	update_time	TIMESTAMP	创建时间	t	f	15	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2025-02-25 09:34:34.65	1	2025-02-25 09:34:34.65	0
944	57	id	BIGINT	主键id	f	t	1	Long	id		24638	f	t	f	=	t	input	1	2025-02-25 09:34:35.412	1	2025-02-25 09:34:35.412	0
945	57	name	VARCHAR	客户名称	f	f	2	String	name		赵六	t	t	t	LIKE	t	input	1	2025-02-25 09:34:35.523	1	2025-02-25 09:34:35.523	0
946	57	mobile_num	VARCHAR	手机号码	f	f	3	String	mobileNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:35.541	1	2025-02-25 09:34:35.541	0
947	57	customer_num	VARCHAR	客户编号	f	f	4	String	customerNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:35.567	1	2025-02-25 09:34:35.567	0
948	57	access_key	VARCHAR	HTTP请求Token	f	f	5	String	accessKey		\N	t	t	t	=	t	input	1	2025-02-25 09:34:35.582	1	2025-02-25 09:34:35.582	0
949	57	http_req_url	VARCHAR	HTTP回调地址	f	f	6	String	httpReqUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-02-25 09:34:35.597	1	2025-02-25 09:34:35.597	0
950	57	http_req_header	VARCHAR	HTTP请求头	t	f	7	String	httpReqHeader		\N	t	t	t	=	t	input	1	2025-02-25 09:34:35.618	1	2025-02-25 09:34:35.618	0
951	57	task_amount_limit	VARCHAR	任务数量限制	t	f	8	String	taskAmountLimit		\N	t	t	t	=	t	input	1	2025-02-25 09:34:35.645	1	2025-02-25 09:34:35.645	0
952	57	status	SMALLINT	状态[0:停用, 1:启用]	t	f	9	Short	status		2	t	t	t	=	t	radio	1	2025-02-25 09:34:35.659	1	2025-02-25 09:34:35.659	0
953	57	create_by	VARCHAR	创建人	t	f	10	String	createBy		\N	t	t	t	=	t	input	1	2025-02-25 09:34:35.679	1	2025-02-25 09:34:35.679	0
954	57	create_time	TIMESTAMP	创建时间	t	f	11	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2025-02-25 09:34:35.699	1	2025-02-25 09:34:35.699	0
955	57	tenant_id	BIGINT	租户编号	f	f	12	Long	tenantId		28212	f	f	f	=	f	input	1	2025-02-25 09:34:35.713	1	2025-02-25 09:34:35.713	0
956	57	update_by	VARCHAR	创建人	t	f	13	String	updateBy		\N	t	t	t	=	t	input	1	2025-02-25 09:34:35.733	1	2025-02-25 09:34:35.733	0
957	57	update_time	TIMESTAMP	创建时间	t	f	14	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2025-02-25 09:34:35.754	1	2025-02-25 09:34:35.754	0
958	58	id	BIGINT	主键id	f	t	1	Long	id		12888	f	t	f	=	t	input	1	2025-02-25 09:34:36.265	1	2025-02-25 09:34:36.265	0
959	58	name	VARCHAR	算法名称	f	f	2	String	name		张三	t	t	t	LIKE	t	input	1	2025-02-25 09:34:36.277	1	2025-02-25 09:34:36.277	0
960	58	model_name	VARCHAR	模型名称	f	f	3	String	modelName		王五	t	t	t	LIKE	t	input	1	2025-02-25 09:34:36.287	1	2025-02-25 09:34:36.287	0
961	58	model_num	VARCHAR	模型编号	f	f	4	String	modelNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:36.296	1	2025-02-25 09:34:36.296	0
1056	62	password	VARCHAR	密码	t	f	14	String	password		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.059	1	2025-03-31 16:12:45.059	0
963	58	core_tech	VARCHAR	核心技术(cnn, knn, svm)	t	f	6	String	coreTech		\N	t	t	t	=	t	input	1	2025-02-25 09:34:36.323	1	2025-02-25 09:34:36.323	0
964	58	shell_key	VARCHAR	执行模型计算的shell key(yolo.py)	t	f	7	String	shellKey		\N	t	t	t	=	t	input	1	2025-02-25 09:34:36.341	1	2025-02-25 09:34:36.341	0
965	58	latest_training_time	TIMESTAMP	最后训练时间	t	f	8	LocalDateTime	latestTrainingTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-02-25 09:34:36.356	1	2025-02-25 09:34:36.356	0
968	58	label_list	VARCHAR	标签列表	t	f	11	String	labelList		\N	t	t	t	=	t	input	1	2025-02-25 09:34:36.396	1	2025-02-25 09:34:36.396	0
969	58	oos_url	VARCHAR	OSS模型地址	t	f	12	String	oosUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-02-25 09:34:36.409	1	2025-02-25 09:34:36.409	0
972	58	create_by	VARCHAR	创建人	t	f	15	String	createBy		\N	t	t	t	=	t	input	1	2025-02-25 09:34:36.468	1	2025-02-25 09:34:36.468	0
973	58	create_time	TIMESTAMP	创建时间	t	f	16	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2025-02-25 09:34:36.486	1	2025-02-25 09:34:36.486	0
974	58	tenant_id	BIGINT	租户编号	f	f	17	Long	tenantId		24128	f	f	f	=	f	input	1	2025-02-25 09:34:36.496	1	2025-02-25 09:34:36.496	0
975	58	update_by	VARCHAR	创建人	t	f	18	String	updateBy		\N	t	t	t	=	t	input	1	2025-02-25 09:34:36.509	1	2025-02-25 09:34:36.509	0
976	58	update_time	TIMESTAMP	创建时间	t	f	19	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2025-02-25 09:34:36.522	1	2025-02-25 09:34:36.522	0
977	59	id	BIGINT	主键id	f	t	1	Long	id		19410	f	t	f	=	t	input	1	2025-02-25 09:34:37.15	1	2025-02-25 09:34:37.15	0
978	59	task_name	VARCHAR	任务名称	f	f	2	String	taskName		赵六	t	t	t	LIKE	t	input	1	2025-02-25 09:34:37.168	1	2025-02-25 09:34:37.168	0
979	59	task_num	VARCHAR	任务编号	f	f	3	String	taskNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.181	1	2025-02-25 09:34:37.181	0
980	59	model_num	VARCHAR	模型编号	f	f	4	String	modelNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.199	1	2025-02-25 09:34:37.199	0
981	59	model_name	VARCHAR	模型名称	f	f	5	String	modelName		李四	t	t	t	LIKE	t	input	1	2025-02-25 09:34:37.213	1	2025-02-25 09:34:37.213	0
982	59	customer_num	VARCHAR	客户编号	f	f	6	String	customerNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.232	1	2025-02-25 09:34:37.232	0
983	59	customer_name	VARCHAR	客户名称	f	f	7	String	customerName		BasicLab	t	t	t	LIKE	t	input	1	2025-02-25 09:34:37.25	1	2025-02-25 09:34:37.25	0
984	59	is_video_device	SMALLINT	是否来源视频设备[0:否, 1:是]	f	f	8	Short	isVideoDevice		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.269	1	2025-02-25 09:34:37.269	0
989	59	video_play_url	VARCHAR	原始视频流播放地址	t	f	13	String	videoPlayUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-02-25 09:34:37.351	1	2025-02-25 09:34:37.351	0
990	59	push_video_play_url	VARCHAR	实时推送的原始视频流播放地址	t	f	14	String	pushVideoPlayUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-02-25 09:34:37.366	1	2025-02-25 09:34:37.366	0
991	59	computing_video_play_url	VARCHAR	实时推送的检测视频流播放地址	t	f	15	String	computingVideoPlayUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-02-25 09:34:37.384	1	2025-02-25 09:34:37.384	0
992	59	stream_server_url	VARCHAR	流媒体服务器地址	t	f	16	String	streamServerUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-02-25 09:34:37.396	1	2025-02-25 09:34:37.396	0
993	59	skip_frame	INTEGER	跳帧数量(每隔多少帧检测一次)	t	f	17	Integer	skipFrame		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.409	1	2025-02-25 09:34:37.409	0
994	59	push_frequency	INTEGER	推送频率(每隔多少秒推送推理结果一次)	t	f	18	Integer	pushFrequency		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.425	1	2025-02-25 09:34:37.425	0
995	59	work_dir	VARCHAR	模型计算工作目录	t	f	19	String	workDir		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.438	1	2025-02-25 09:34:37.438	0
996	59	shell_key	VARCHAR	执行模型计算的shell key(yolo.py)	t	f	20	String	shellKey		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.451	1	2025-02-25 09:34:37.451	0
997	59	first_exec_time	TIMESTAMP	首次执行时间	t	f	21	LocalDateTime	firstExecTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-02-25 09:34:37.465	1	2025-02-25 09:34:37.465	0
998	59	latest_exec_time	TIMESTAMP	最后执行时间	t	f	22	LocalDateTime	latestExecTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-02-25 09:34:37.48	1	2025-02-25 09:34:37.48	0
999	59	alarm_amount	INTEGER	告警次数	t	f	23	Integer	alarmAmount		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.496	1	2025-02-25 09:34:37.496	0
1000	59	latest_alarm_time	TIMESTAMP	最后告警时间	t	f	24	LocalDateTime	latestAlarmTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-02-25 09:34:37.509	1	2025-02-25 09:34:37.509	0
1001	59	order_num	INTEGER	排序号	t	f	25	Integer	orderNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.522	1	2025-02-25 09:34:37.522	0
1002	59	task_status	SMALLINT	任务状态[0:停用, 1:启用]	t	f	26	Short	taskStatus		2	t	t	t	=	t	radio	1	2025-02-25 09:34:37.541	1	2025-02-25 09:34:37.541	0
1003	59	pid	VARCHAR	任务进程	t	f	27	String	pid		25294	t	t	t	=	t	input	1	2025-02-25 09:34:37.562	1	2025-02-25 09:34:37.562	0
1004	59	pid_start_time	TIMESTAMP	任务进程启动时间	t	f	28	LocalDateTime	pidStartTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-02-25 09:34:37.576	1	2025-02-25 09:34:37.576	0
1005	59	pid_stop_time	TIMESTAMP	任务进程停止时间	t	f	29	LocalDateTime	pidStopTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-02-25 09:34:37.591	1	2025-02-25 09:34:37.591	0
1057	62	stream	VARCHAR	码流编号[0:主码流,1：子码流]	t	f	15	String	stream		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.073	1	2025-03-31 16:12:45.073	0
962	58	algorithm_type	INTEGER	算法分类[0:吸烟, 1:打架, 2:人行, 3:人脸, 4:安全帽, 5:口罩, 6:汽车, 7:摩托车, 8:电瓶车]	t	f	5	Integer	algorithmType		2	t	t	t	=	t	select	1	2025-02-25 09:34:36.308	1	2025-02-25 09:34:36.308	1
966	58	conf_threshold	VARCHAR	置信度阈值	f	f	9	String	confThreshold		\N	t	t	t	=	t	input	1	2025-02-25 09:34:36.369	1	2025-02-25 09:34:36.369	1
967	58	nms_threshold	VARCHAR	NMS阈值(非极大值抑制IOU)	f	f	10	String	nmsThreshold		\N	t	t	t	=	t	input	1	2025-02-25 09:34:36.382	1	2025-02-25 09:34:36.382	1
970	58	order_num	VARCHAR	排序号	t	f	13	String	orderNum		\N	t	t	t	=	t	input	1	2025-02-25 09:34:36.428	1	2025-02-25 09:34:36.428	1
1006	59	restart_count	INTEGER	当前系统已经自动重启过的次数,运行异常时，后台会自动重启推理脚本并记录重启次数;在页面上手动关闭任务后改值重置为0	t	f	30	Integer	restartCount		3749	t	t	t	=	t	input	1	2025-02-25 09:34:37.602	1	2025-02-25 09:34:37.602	0
1007	59	restart_msg	VARCHAR	重启失败的原因	t	f	31	String	restartMsg		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.62	1	2025-02-25 09:34:37.62	0
1008	59	create_by	VARCHAR	创建人	t	f	32	String	createBy		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.635	1	2025-02-25 09:34:37.635	0
1009	59	create_time	TIMESTAMP	创建时间	t	f	33	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2025-02-25 09:34:37.648	1	2025-02-25 09:34:37.648	0
1010	59	tenant_id	BIGINT	租户编号	f	f	34	Long	tenantId		18199	f	f	f	=	f	input	1	2025-02-25 09:34:37.663	1	2025-02-25 09:34:37.663	0
1011	59	update_by	VARCHAR	创建人	t	f	35	String	updateBy		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.686	1	2025-02-25 09:34:37.686	0
1012	59	update_time	TIMESTAMP	创建时间	t	f	36	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2025-02-25 09:34:37.705	1	2025-02-25 09:34:37.705	0
1013	60	id	BIGINT	主键id	f	t	1	Long	id		15316	f	t	f	=	t	input	1	2025-02-25 11:49:54.081	1	2025-02-25 11:49:54.081	0
1014	60	push_time	TIMESTAMP	推送时间	f	f	2	LocalDateTime	pushTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-02-25 11:49:54.115	1	2025-02-25 11:49:54.115	0
1015	60	status	SMALLINT	推送状态[0:失败, 1:成功]	f	f	3	Short	status		2	t	t	t	=	t	radio	1	2025-02-25 11:49:54.139	1	2025-02-25 11:49:54.139	0
1016	60	http_req_url	VARCHAR	http请求地址	f	f	4	String	httpReqUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-02-25 11:49:54.16	1	2025-02-25 11:49:54.16	0
1017	60	http_req_header	VARCHAR	http请求头	t	f	5	String	httpReqHeader		\N	t	t	t	=	t	input	1	2025-02-25 11:49:54.178	1	2025-02-25 11:49:54.178	0
1018	60	http_req_param	VARCHAR	http请求参数	t	f	6	String	httpReqParam		\N	t	t	t	=	t	input	1	2025-02-25 11:49:54.206	1	2025-02-25 11:49:54.206	0
1019	60	http_result	VARCHAR	http返回结果	t	f	7	String	httpResult		\N	t	t	t	=	t	input	1	2025-02-25 11:49:54.222	1	2025-02-25 11:49:54.222	0
1020	60	task_num	VARCHAR	任务编号	f	f	8	String	taskNum		\N	t	t	t	=	t	input	1	2025-02-25 11:49:54.239	1	2025-02-25 11:49:54.239	0
1021	60	task_name	VARCHAR	任务名称	f	f	9	String	taskName		王五	t	t	t	LIKE	t	input	1	2025-02-25 11:49:54.253	1	2025-02-25 11:49:54.253	0
1022	60	model_num	VARCHAR	模型编号	f	f	10	String	modelNum		\N	t	t	t	=	t	input	1	2025-02-25 11:49:54.272	1	2025-02-25 11:49:54.272	0
1023	60	model_name	VARCHAR	模型名称	f	f	11	String	modelName		赵六	t	t	t	LIKE	t	input	1	2025-02-25 11:49:54.291	1	2025-02-25 11:49:54.291	0
1024	60	customer_num	VARCHAR	客户编号	f	f	12	String	customerNum		\N	t	t	t	=	t	input	1	2025-02-25 11:49:54.316	1	2025-02-25 11:49:54.316	0
1025	60	customer_name	VARCHAR	客户名称	f	f	13	String	customerName		赵六	t	t	t	LIKE	t	input	1	2025-02-25 11:49:54.332	1	2025-02-25 11:49:54.332	0
1026	60	create_by	VARCHAR	创建人	t	f	14	String	createBy		\N	t	t	t	=	t	input	1	2025-02-25 11:49:54.35	1	2025-02-25 11:49:54.35	0
1027	60	create_time	TIMESTAMP	创建时间	t	f	15	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2025-02-25 11:49:54.364	1	2025-02-25 11:49:54.364	0
1028	60	tenant_id	BIGINT	租户编号	f	f	16	Long	tenantId		32251	f	f	f	=	f	input	1	2025-02-25 11:49:54.384	1	2025-02-25 11:49:54.384	0
1029	60	update_by	VARCHAR	创建人	t	f	17	String	updateBy		\N	t	t	t	=	t	input	1	2025-02-25 11:49:54.4	1	2025-02-25 11:49:54.4	0
1030	60	update_time	TIMESTAMP	创建时间	t	f	18	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2025-02-25 11:49:54.422	1	2025-02-25 11:49:54.422	0
1032	61	create_by	VARCHAR	创建人	t	f	2	String	createBy		\N	t	t	t	=	t	input	1	2025-03-31 16:12:44.233	1	2025-03-31 16:12:44.233	0
1033	61	create_time	TIMESTAMP	创建时间	t	f	3	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2025-03-31 16:12:44.246	1	2025-03-31 16:12:44.246	0
1034	61	tenant_id	BIGINT	租户编号	f	f	4	Long	tenantId		21589	f	f	f	=	f	input	1	2025-03-31 16:12:44.26	1	2025-03-31 16:12:44.26	0
1035	61	update_by	VARCHAR	创建人	t	f	5	String	updateBy		\N	t	t	t	=	t	input	1	2025-03-31 16:12:44.275	1	2025-03-31 16:12:44.275	0
1036	61	update_time	TIMESTAMP	创建时间	t	f	6	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2025-03-31 16:12:44.29	1	2025-03-31 16:12:44.29	0
1037	61	deleted	SMALLINT	是否删除	f	f	7	Short	deleted		\N	f	f	f	=	f	input	1	2025-03-31 16:12:44.301	1	2025-03-31 16:12:44.301	0
1038	61	file_url	VARCHAR	文件路径	t	f	8	String	fileUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-03-31 16:12:44.313	1	2025-03-31 16:12:44.313	0
1039	61	event_time	TIMESTAMP	告警事件时间	t	f	9	LocalDateTime	eventTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-03-31 16:12:44.328	1	2025-03-31 16:12:44.328	0
1040	61	algorithm_video_id	BIGINT	计算设备ID	t	f	10	Long	algorithmVideoId		9603	t	t	t	=	t	input	1	2025-03-31 16:12:44.339	1	2025-03-31 16:12:44.339	0
1041	61	device_name	VARCHAR	设备名称	t	f	11	String	deviceName		赵六	t	t	t	LIKE	t	input	1	2025-03-31 16:12:44.352	1	2025-03-31 16:12:44.352	0
1042	61	duration	VARCHAR	告警持续时间	t	f	12	String	duration		\N	t	t	t	=	t	input	1	2025-03-31 16:12:44.366	1	2025-03-31 16:12:44.366	0
1044	62	create_by	VARCHAR	创建人	t	f	2	String	createBy		\N	t	t	t	=	t	input	1	2025-03-31 16:12:44.892	1	2025-03-31 16:12:44.892	0
1045	62	create_time	TIMESTAMP	创建时间	t	f	3	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2025-03-31 16:12:44.913	1	2025-03-31 16:12:44.913	0
1046	62	tenant_id	BIGINT	租户编号	f	f	4	Long	tenantId		6502	f	f	f	=	f	input	1	2025-03-31 16:12:44.929	1	2025-03-31 16:12:44.929	0
1047	62	update_by	VARCHAR	创建人	t	f	5	String	updateBy		\N	t	t	t	=	t	input	1	2025-03-31 16:12:44.954	1	2025-03-31 16:12:44.954	0
1048	62	update_time	TIMESTAMP	创建时间	t	f	6	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2025-03-31 16:12:44.968	1	2025-03-31 16:12:44.968	0
1049	62	deleted	SMALLINT	是否删除	f	f	7	Short	deleted		\N	f	f	f	=	f	input	1	2025-03-31 16:12:44.978	1	2025-03-31 16:12:44.978	0
1050	62	parent_id	VARCHAR	父ID	t	f	8	String	parentId		346	t	t	t	=	t	input	1	2025-03-31 16:12:44.989	1	2025-03-31 16:12:44.989	0
1051	62	device_name	VARCHAR	设备名称	t	f	9	String	deviceName		BasicLab	t	t	t	LIKE	t	input	1	2025-03-31 16:12:45	1	2025-03-31 16:12:45	0
1052	62	device_type	INTEGER	设备类型[0:摄像头,1:视频源,2:海康NVR,3:大华NVR]	t	f	10	Integer	deviceType		1	t	t	t	=	t	select	1	2025-03-31 16:12:45.01	1	2025-03-31 16:12:45.01	0
1053	62	ip	VARCHAR	IP地址	t	f	11	String	ip		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.019	1	2025-03-31 16:12:45.019	0
1054	62	port	VARCHAR	端口	t	f	12	String	port		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.035	1	2025-03-31 16:12:45.035	0
1031	61	id	BIGINT	主键id	f	t	1	Long	id		17655	f	t	f	=	t	input	1	2025-03-31 16:12:44.21	1	2025-03-31 16:12:44.21	1
1058	62	stream_server_url	VARCHAR	流媒体服务器地址	t	f	16	String	streamServerUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-03-31 16:12:45.088	1	2025-03-31 16:12:45.088	0
1059	62	source	VARCHAR	原始摄像头rtsp取流地址	t	f	17	String	source		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.108	1	2025-03-31 16:12:45.108	0
1060	62	ai_rtmp_stream	VARCHAR	AI处理后的rtmp视频流	t	f	18	String	aiRtmpStream		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.127	1	2025-03-31 16:12:45.127	0
1061	62	ai_http_stream	VARCHAR	AI处理后的http视频流	t	f	19	String	aiHttpStream		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.141	1	2025-03-31 16:12:45.141	0
1062	62	ai_webrtc_stream	VARCHAR	AI处理后的webrtc视频流	t	f	20	String	aiWebrtcStream		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.156	1	2025-03-31 16:12:45.156	0
1063	62	algorithm_model_id	BIGINT	计算模型ID	t	f	21	Long	algorithmModelId		14990	t	t	t	=	t	input	1	2025-03-31 16:12:45.169	1	2025-03-31 16:12:45.169	0
1064	62	is_task_enabled	SMALLINT	是否已启动任务	t	f	22	Short	isTaskEnabled		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.189	1	2025-03-31 16:12:45.189	0
1065	62	task_args	VARCHAR	任务参数	t	f	23	String	taskArgs		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.207	1	2025-03-31 16:12:45.207	0
1066	62	region	VARCHAR	检测区域	t	f	24	String	region		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.22	1	2025-03-31 16:12:45.22	0
1067	62	alert_args	VARCHAR	告警参数	t	f	25	String	alertArgs		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.234	1	2025-03-31 16:12:45.234	0
1068	62	is_enable_forward	SMALLINT	是否转发原视频流到平台	t	f	26	Short	isEnableForward		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.246	1	2025-03-31 16:12:45.246	0
1069	62	mac	VARCHAR	MAC地址	t	f	27	String	mac		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.258	1	2025-03-31 16:12:45.258	0
1070	62	manufacturer	VARCHAR	制造商	t	f	28	String	manufacturer		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.268	1	2025-03-31 16:12:45.268	0
1071	62	device_model	VARCHAR	设备型号	t	f	29	String	deviceModel		\N	t	t	t	=	t	input	1	2025-03-31 16:12:45.277	1	2025-03-31 16:12:45.277	0
1072	60	deleted	SMALLINT	是否删除	f	f	1	Short	deleted		\N	f	f	f	=	f	input	1	2025-03-31 16:12:52.5	1	2025-03-31 16:12:52.5	0
1073	59	deleted	SMALLINT	是否删除	f	f	1	Short	deleted		\N	f	f	f	=	f	input	1	2025-03-31 16:12:56.367	1	2025-03-31 16:12:56.367	0
1074	59	algorithm_video_id	BIGINT	计算设备ID	t	f	2	Long	algorithmVideoId		14483	t	t	t	=	t	input	1	2025-03-31 16:12:56.377	1	2025-03-31 16:12:56.377	0
1075	59	ai_rtmp_stream	VARCHAR	AI处理后的rtmp视频流	t	f	3	String	aiRtmpStream		\N	t	t	t	=	t	input	1	2025-03-31 16:12:56.388	1	2025-03-31 16:12:56.388	0
1076	59	source	VARCHAR	原始摄像头rtsp取流地址	t	f	4	String	source		\N	t	t	t	=	t	input	1	2025-03-31 16:12:56.399	1	2025-03-31 16:12:56.399	0
1077	59	ai_http_stream	VARCHAR	AI处理后的http视频流	t	f	5	String	aiHttpStream		\N	t	t	t	=	t	input	1	2025-03-31 16:12:56.409	1	2025-03-31 16:12:56.409	0
1078	59	ai_webrtc_stream	VARCHAR	AI处理后的webrtc视频流	t	f	6	String	aiWebrtcStream		\N	t	t	t	=	t	input	1	2025-03-31 16:12:56.419	1	2025-03-31 16:12:56.419	0
985	59	device_id	VARCHAR	设备序列号	t	f	9	String	deviceId		13863	t	t	t	=	t	input	1	2025-02-25 09:34:37.288	1	2025-02-25 09:34:37.288	1
986	59	channel_id	VARCHAR	通道号	t	f	10	String	channelId		10431	t	t	t	=	t	input	1	2025-02-25 09:34:37.306	1	2025-02-25 09:34:37.306	1
987	59	cron_expression	VARCHAR	点播Cron表达式	t	f	11	String	cronExpression		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.319	1	2025-02-25 09:34:37.319	1
988	59	video_base_info	VARCHAR	原始视频基本信息(协议，带宽，图像大小)json	t	f	12	String	videoBaseInfo		\N	t	t	t	=	t	input	1	2025-02-25 09:34:37.338	1	2025-02-25 09:34:37.338	1
1079	58	algorithm_type	INTEGER	算法分类[0:吸烟, 1:打架, 2:行人, 3:人脸, 4:安全帽, 5:口罩, 6:汽车, 7:摩托车, 8:电瓶车]	t	f	1	Integer	algorithmType		1	t	t	t	=	t	select	1	2025-03-31 16:12:59.467	1	2025-03-31 16:12:59.467	0
1080	58	conf_threshold	REAL	置信度阈值	f	f	2	Double	confThreshold		\N	t	t	t	=	t	input	1	2025-03-31 16:12:59.485	1	2025-03-31 16:12:59.485	0
1081	58	nms_threshold	REAL	NMS阈值(非极大值抑制IOU)	f	f	3	Double	nmsThreshold		\N	t	t	t	=	t	input	1	2025-03-31 16:12:59.501	1	2025-03-31 16:12:59.501	0
1082	58	order_num	INTEGER	排序号	t	f	4	Integer	orderNum		\N	t	t	t	=	t	input	1	2025-03-31 16:12:59.512	1	2025-03-31 16:12:59.512	0
1083	58	status	SMALLINT	状态[0:未验证,1:已验证,2:已发布]	f	f	5	Short	status		2	t	t	t	=	t	radio	1	2025-03-31 16:12:59.529	1	2025-03-31 16:12:59.529	0
1084	58	is_enabled	SMALLINT	是否启用[0:停用, 1:启用]	t	f	6	Short	isEnabled		\N	t	t	t	=	t	input	1	2025-03-31 16:12:59.547	1	2025-03-31 16:12:59.547	0
1085	58	deleted	SMALLINT	是否删除	f	f	7	Short	deleted		\N	f	f	f	=	f	input	1	2025-03-31 16:12:59.562	1	2025-03-31 16:12:59.562	0
1086	58	publish_time	TIMESTAMP	发布时间	t	f	8	LocalDateTime	publishTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-03-31 16:12:59.573	1	2025-03-31 16:12:59.573	0
971	58	status	SMALLINT	状态[0:停用, 1:启用]	f	f	14	Short	status		1	t	t	t	=	t	radio	1	2025-02-25 09:34:36.449	1	2025-02-25 09:34:36.449	1
1087	57	deleted	SMALLINT	是否删除	f	f	1	Short	deleted		\N	f	f	f	=	f	input	1	2025-03-31 16:13:03.19	1	2025-03-31 16:13:03.19	0
1088	62	id	BIGINT	主键id	f	f	1	Long	id		18992	f	t	f	=	t	input	1	2025-03-31 16:13:06.441	1	2025-03-31 16:13:06.441	0
1043	62	id	BIGINT	主键id	f	t	1	Long	id		2490	f	t	f	=	t	input	1	2025-03-31 16:12:44.87	1	2025-03-31 16:12:44.87	1
1089	61	id	BIGINT	主键id	f	f	1	Long	id		26618	f	t	f	=	t	input	1	2025-03-31 16:13:07.997	1	2025-03-31 16:13:07.997	0
1090	56	deleted	SMALLINT	是否删除	f	f	1	Short	deleted		\N	f	f	f	=	f	input	1	2025-03-31 16:13:23.145	1	2025-03-31 16:13:23.145	0
1091	56	object	VARCHAR	告警对象	t	f	2	String	object		\N	t	t	t	=	t	input	1	2025-03-31 16:13:23.158	1	2025-03-31 16:13:23.158	0
1092	56	region	VARCHAR	检测区域	t	f	3	String	region		\N	t	t	t	=	t	input	1	2025-03-31 16:13:23.168	1	2025-03-31 16:13:23.168	0
1093	56	event	VARCHAR	告警事件	t	f	4	String	event		\N	t	t	t	=	t	input	1	2025-03-31 16:13:23.189	1	2025-03-31 16:13:23.189	0
1094	56	event_message	VARCHAR	告警事件内容	t	f	5	String	eventMessage		\N	t	t	t	=	t	input	1	2025-03-31 16:13:23.202	1	2025-03-31 16:13:23.202	0
1095	56	event_time	TIMESTAMP	告警事件时间	t	f	6	LocalDateTime	eventTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-03-31 16:13:23.214	1	2025-03-31 16:13:23.214	0
1096	56	algorithm_video_id	BIGINT	计算设备ID	t	f	7	Long	algorithmVideoId		989	t	t	t	=	t	input	1	2025-03-31 16:13:23.227	1	2025-03-31 16:13:23.227	0
1097	56	device_name	VARCHAR	设备名称	t	f	8	String	deviceName		王五	t	t	t	LIKE	t	input	1	2025-03-31 16:13:23.243	1	2025-03-31 16:13:23.243	0
1098	56	image_url	VARCHAR	告警图片地址	t	f	9	String	imageUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-03-31 16:13:23.256	1	2025-03-31 16:13:23.256	0
1099	56	record_url	VARCHAR	告警录像地址	t	f	10	String	recordUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-03-31 16:13:23.267	1	2025-03-31 16:13:23.267	0
1100	63	id	BIGINT	主键id	f	t	1	Long	id		6641	f	t	f	=	t	input	1	2025-03-31 18:02:01.971	1	2025-03-31 18:02:01.971	1
1101	63	ip	VARCHAR	IP地址	t	f	2	String	ip		\N	t	t	t	=	t	input	1	2025-03-31 18:02:01.987	1	2025-03-31 18:02:01.987	1
1102	63	port	VARCHAR	端口	t	f	3	String	port		\N	t	t	t	=	t	input	1	2025-03-31 18:02:01.998	1	2025-03-31 18:02:01.998	1
1103	63	username	VARCHAR	用户名	t	f	4	String	username		BasicLab	t	t	t	LIKE	t	input	1	2025-03-31 18:02:02.007	1	2025-03-31 18:02:02.007	1
1104	63	password	VARCHAR	密码	t	f	5	String	password		\N	t	t	t	=	t	input	1	2025-03-31 18:02:02.018	1	2025-03-31 18:02:02.018	1
1105	63	device_name	VARCHAR	设备名称	t	f	6	String	deviceName		王五	t	t	t	LIKE	t	input	1	2025-03-31 18:02:02.028	1	2025-03-31 18:02:02.028	1
1106	63	device_type	SMALLINT	设备类型[0:海康NVR,1:大华NVR]	t	f	7	Short	deviceType		2	t	t	t	=	t	select	1	2025-03-31 18:02:02.037	1	2025-03-31 18:02:02.037	1
1107	63	device_model	VARCHAR	设备型号	t	f	8	String	deviceModel		\N	t	t	t	=	t	input	1	2025-03-31 18:02:02.045	1	2025-03-31 18:02:02.045	1
1108	63	create_by	VARCHAR	创建人	t	f	9	String	createBy		\N	t	t	t	=	t	input	1	2025-03-31 18:02:02.054	1	2025-03-31 18:02:02.054	1
1109	63	create_time	TIMESTAMP	创建时间	t	f	10	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2025-03-31 18:02:02.063	1	2025-03-31 18:02:02.063	1
1110	63	tenant_id	BIGINT	租户编号	f	f	11	Long	tenantId		26567	f	f	f	=	f	input	1	2025-03-31 18:02:02.071	1	2025-03-31 18:02:02.071	1
1111	63	update_by	VARCHAR	创建人	t	f	12	String	updateBy		\N	t	t	t	=	t	input	1	2025-03-31 18:02:02.101	1	2025-03-31 18:02:02.101	1
1112	63	update_time	TIMESTAMP	创建时间	t	f	13	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2025-03-31 18:02:02.112	1	2025-03-31 18:02:02.112	1
1113	63	deleted	SMALLINT	是否删除	f	f	14	Short	deleted		\N	f	f	f	=	f	input	1	2025-03-31 18:02:02.12	1	2025-03-31 18:02:02.12	1
1114	64	id	BIGINT	主键ID	f	t	1	Long	id		10782	f	t	f	=	t	input	1	2025-06-15 07:13:17.408	1	2025-06-15 07:13:17.408	0
1115	64	dataset_code	VARCHAR	数据集编码	f	f	2	String	datasetCode		\N	t	t	t	=	t	input	1	2025-06-15 07:13:17.495	1	2025-06-15 07:13:17.495	0
1116	64	name	VARCHAR	数据集名称	f	f	3	String	name		赵六	t	t	t	LIKE	t	input	1	2025-06-15 07:13:17.575	1	2025-06-15 07:13:17.575	0
1117	64	cover_path	VARCHAR	封面地址	t	f	4	String	coverPath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:17.664	1	2025-06-15 07:13:17.664	0
1118	64	description	VARCHAR	描述	t	f	5	String	description		你猜	t	t	t	=	t	editor	1	2025-06-15 07:13:17.726	1	2025-06-15 07:13:17.726	0
1119	64	dataset_type	SMALLINT	数据集类型，1-图片；2-文本	f	f	6	Short	datasetType		2	t	t	t	=	t	select	1	2025-06-15 07:13:17.793	1	2025-06-15 07:13:17.793	0
1120	64	audit	SMALLINT	数据集状态：0-审核通过；1-待审核；2-驳回	f	f	7	Short	audit		\N	t	t	t	=	t	input	1	2025-06-15 07:13:17.834	1	2025-06-15 07:13:17.834	0
1121	64	reason	VARCHAR	审核驳回理由	t	f	8	String	reason		不好	t	t	t	=	t	input	1	2025-06-15 07:13:17.894	1	2025-06-15 07:13:17.894	0
1122	65	id	BIGINT	主键ID	f	t	1	Long	id		1456	f	t	f	=	t	input	1	2025-06-15 07:13:19.425	1	2025-06-15 07:13:19.425	0
1123	65	dataset_id	BIGINT	数据集ID	f	f	2	Long	datasetId		16395	t	t	t	=	t	input	1	2025-06-15 07:13:19.494	1	2025-06-15 07:13:19.494	0
1124	65	name	VARCHAR	图片名称	f	f	3	String	name		王五	t	t	t	LIKE	t	input	1	2025-06-15 07:13:19.527	1	2025-06-15 07:13:19.527	0
1125	65	image_path	VARCHAR	图片地址	f	f	4	String	imagePath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:19.573	1	2025-06-15 07:13:19.573	0
1126	65	select_count	INTEGER	选用次数	t	f	5	Integer	selectCount		10725	t	t	t	=	t	input	1	2025-06-15 07:13:19.615	1	2025-06-15 07:13:19.615	0
1127	65	last_select_time	TIMESTAMP	最后一次选用时间	t	f	6	LocalDateTime	lastSelectTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-06-15 07:13:19.675	1	2025-06-15 07:13:19.675	0
1128	65	wide	INTEGER	图片宽度	t	f	7	Integer	wide		\N	t	t	t	=	t	input	1	2025-06-15 07:13:19.73	1	2025-06-15 07:13:19.73	0
1129	65	high	INTEGER	图片高度	t	f	8	Integer	high		\N	t	t	t	=	t	input	1	2025-06-15 07:13:19.786	1	2025-06-15 07:13:19.786	0
1130	65	size	BIGINT	图片大小	t	f	9	Long	size		\N	t	t	t	=	t	input	1	2025-06-15 07:13:19.844	1	2025-06-15 07:13:19.844	0
1131	65	tags	VARCHAR	标签，多个以逗号分割	t	f	10	String	tags		\N	t	t	t	=	t	input	1	2025-06-15 07:13:19.885	1	2025-06-15 07:13:19.885	0
1132	65	dataset_video_id	BIGINT	视频ID（来源为视频切片）	t	f	11	Long	datasetVideoId		9996	t	t	t	=	t	input	1	2025-06-15 07:13:19.925	1	2025-06-15 07:13:19.925	0
1133	66	id	BIGINT	主键ID	f	t	1	Long	id		25811	f	t	f	=	t	input	1	2025-06-15 07:13:21.455	1	2025-06-15 07:13:21.455	0
1134	66	tag_number	VARCHAR	标签编号	f	f	2	String	tagNumber		\N	t	t	t	=	t	input	1	2025-06-15 07:13:21.515	1	2025-06-15 07:13:21.515	0
1135	66	name	VARCHAR	标签名称	f	f	3	String	name		王五	t	t	t	LIKE	t	input	1	2025-06-15 07:13:21.565	1	2025-06-15 07:13:21.565	0
1136	66	color	VARCHAR	标签颜色	t	f	4	String	color		\N	t	t	t	=	t	input	1	2025-06-15 07:13:21.625	1	2025-06-15 07:13:21.625	0
1137	66	dataset_id	BIGINT	数据集ID	f	f	5	Long	datasetId		11425	t	t	t	=	t	input	1	2025-06-15 07:13:21.685	1	2025-06-15 07:13:21.685	0
1138	66	warehouse_id	BIGINT	数据仓ID	t	f	6	Long	warehouseId		1613	t	t	t	=	t	input	1	2025-06-15 07:13:21.745	1	2025-06-15 07:13:21.745	0
1139	66	description	VARCHAR	描述	t	f	7	String	description		随便	t	t	t	=	t	editor	1	2025-06-15 07:13:21.815	1	2025-06-15 07:13:21.815	0
1140	67	id	BIGINT	主键ID	f	t	1	Long	id		10732	f	t	f	=	t	input	1	2025-06-15 07:13:23.365	1	2025-06-15 07:13:23.365	0
1141	67	name	VARCHAR	任务名称	f	f	2	String	name		王五	t	t	t	LIKE	t	input	1	2025-06-15 07:13:23.435	1	2025-06-15 07:13:23.435	0
1142	67	dataset_id	BIGINT	数据集ID	f	f	3	Long	datasetId		19968	t	t	t	=	t	input	1	2025-06-15 07:13:23.484	1	2025-06-15 07:13:23.484	0
1143	67	data_range	SMALLINT	数据范围[0:全部,1:无标注,2:有标注]	f	f	4	Short	dataRange		\N	t	t	t	=	t	input	1	2025-06-15 07:13:23.535	1	2025-06-15 07:13:23.535	0
1144	67	planned_quantity	INTEGER	计划标注数量	f	f	5	Integer	plannedQuantity		\N	t	t	t	=	t	input	1	2025-06-15 07:13:23.593	1	2025-06-15 07:13:23.593	0
1145	67	marked_quantity	INTEGER	已标注数量	t	f	6	Integer	markedQuantity		\N	t	t	t	=	t	input	1	2025-06-15 07:13:23.645	1	2025-06-15 07:13:23.645	0
1146	67	new_label	SMALLINT	新标签入库[0:否,1:是]	f	f	7	Short	newLabel		\N	t	t	t	=	t	input	1	2025-06-15 07:13:23.705	1	2025-06-15 07:13:23.705	0
1147	67	finish_status	SMALLINT	完成状态[0:未完成,1:已完成]	f	f	8	Short	finishStatus		2	t	t	t	=	t	radio	1	2025-06-15 07:13:23.758	1	2025-06-15 07:13:23.758	0
1148	67	finish_time	TIMESTAMP	完成时间	t	f	9	LocalDateTime	finishTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-06-15 07:13:23.825	1	2025-06-15 07:13:23.825	0
1149	67	model_id	BIGINT	模型ID	t	f	10	Long	modelId		31092	t	t	t	=	t	input	1	2025-06-15 07:13:23.905	1	2025-06-15 07:13:23.905	0
1150	67	model_serve_id	BIGINT	模型服务ID	t	f	11	Long	modelServeId		19146	t	t	t	=	t	input	1	2025-06-15 07:13:23.975	1	2025-06-15 07:13:23.975	0
1151	67	is_stop	SMALLINT	是否停止[0:否,1:是]	f	f	12	Short	isStop		\N	t	t	t	=	t	input	1	2025-06-15 07:13:24.015	1	2025-06-15 07:13:24.015	0
1152	67	task_type	SMALLINT	任务类型[0:智能标注,1:人员标注,2:审核]	f	f	13	Short	taskType		2	t	t	t	=	t	select	1	2025-06-15 07:13:24.098	1	2025-06-15 07:13:24.098	0
1153	67	end_time	TIMESTAMP	截止时间(人员或审核)	t	f	14	LocalDateTime	endTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-06-15 07:13:24.158	1	2025-06-15 07:13:24.158	0
1154	67	not_target_count	INTEGER	无目标数量	t	f	15	Integer	notTargetCount		19787	t	t	t	=	t	input	1	2025-06-15 07:13:24.215	1	2025-06-15 07:13:24.215	0
1155	68	id	BIGINT	主键ID	f	t	1	Long	id		2883	f	t	f	=	t	input	1	2025-06-15 07:13:26.045	1	2025-06-15 07:13:26.045	0
1156	68	dataset_image_id	BIGINT	数据集图片ID	f	f	2	Long	datasetImageId		19448	t	t	t	=	t	input	1	2025-06-15 07:13:26.104	1	2025-06-15 07:13:26.104	0
1157	68	model_id	BIGINT	模型ID	t	f	3	Long	modelId		11863	t	t	t	=	t	input	1	2025-06-15 07:13:26.144	1	2025-06-15 07:13:26.144	0
1158	68	has_anno	SMALLINT	是否有标注[0:无,1:有]	f	f	4	Short	hasAnno		\N	t	t	t	=	t	input	1	2025-06-15 07:13:26.179	1	2025-06-15 07:13:26.179	0
1159	68	annos	VARCHAR	标注信息	f	f	5	String	annos		\N	t	t	t	=	t	input	1	2025-06-15 07:13:26.235	1	2025-06-15 07:13:26.235	0
1160	68	task_type	SMALLINT	任务类型[0:智能标注,1:人员标注,2:审核]	f	f	6	Short	taskType		2	t	t	t	=	t	select	1	2025-06-15 07:13:26.295	1	2025-06-15 07:13:26.295	0
1161	68	user_id	BIGINT	标注或审核的用户id	f	f	7	Long	userId		26989	t	t	t	=	t	input	1	2025-06-15 07:13:26.345	1	2025-06-15 07:13:26.345	0
1162	68	pass_status	SMALLINT	通过状态[0:待审核,1:通过,2:驳回]	f	f	8	Short	passStatus		2	t	t	t	=	t	radio	1	2025-06-15 07:13:26.39	1	2025-06-15 07:13:26.39	0
1163	68	task_id	BIGINT	任务ID	f	f	9	Long	taskId		2813	t	t	t	=	t	input	1	2025-06-15 07:13:26.43	1	2025-06-15 07:13:26.43	0
1164	68	reason	VARCHAR	驳回原因	t	f	10	String	reason		不喜欢	t	t	t	=	t	input	1	2025-06-15 07:13:26.485	1	2025-06-15 07:13:26.485	0
1165	68	is_update	SMALLINT	是否修改过[0:否,1是]	f	f	11	Short	isUpdate		\N	t	t	t	BETWEEN	t	datetime	1	2025-06-15 07:13:26.525	1	2025-06-15 07:13:26.525	0
1166	69	id	BIGINT	主键ID	f	t	1	Long	id		28821	f	t	f	=	t	input	1	2025-06-15 07:13:28.125	1	2025-06-15 07:13:28.125	0
1167	69	task_id	BIGINT	任务ID	f	f	2	Long	taskId		7599	t	t	t	=	t	input	1	2025-06-15 07:13:28.175	1	2025-06-15 07:13:28.175	0
1168	69	user_id	BIGINT	标注用户ID	f	f	3	Long	userId		10844	t	t	t	=	t	input	1	2025-06-15 07:13:28.213	1	2025-06-15 07:13:28.213	0
1169	69	audit_user_id	BIGINT	审核用户ID	t	f	4	Long	auditUserId		5614	t	t	t	=	t	input	1	2025-06-15 07:13:28.254	1	2025-06-15 07:13:28.254	0
1170	70	id	BIGINT	主键ID	f	t	1	Long	id		7832	f	t	f	=	t	input	1	2025-06-15 07:13:29.505	1	2025-06-15 07:13:29.505	0
1171	70	dataset_id	BIGINT	数据集ID	f	f	2	Long	datasetId		16405	t	t	t	=	t	input	1	2025-06-15 07:13:29.549	1	2025-06-15 07:13:29.549	0
1172	70	video_path	VARCHAR	视频地址	f	f	3	String	videoPath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:29.589	1	2025-06-15 07:13:29.589	0
1173	70	cover_path	VARCHAR	封面地址	t	f	4	String	coverPath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:29.654	1	2025-06-15 07:13:29.654	0
1174	70	description	VARCHAR	描述	t	f	5	String	description		你猜	t	t	t	=	t	editor	1	2025-06-15 07:13:29.695	1	2025-06-15 07:13:29.695	0
1175	71	id	BIGINT	主键ID	f	t	1	Long	id		26327	f	t	f	=	t	input	1	2025-06-15 07:13:31.055	1	2025-06-15 07:13:31.055	0
1176	71	name	VARCHAR	模型名称	f	f	2	String	name		赵六	t	t	t	LIKE	t	input	1	2025-06-15 07:13:31.115	1	2025-06-15 07:13:31.115	0
1177	71	description	VARCHAR	描述	t	f	3	String	description		随便	t	t	t	=	t	editor	1	2025-06-15 07:13:31.175	1	2025-06-15 07:13:31.175	0
1178	71	cover_path	VARCHAR	封面地址	t	f	4	String	coverPath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:31.219	1	2025-06-15 07:13:31.219	0
1179	71	version	VARCHAR	模型版本	t	f	5	String	version		\N	t	t	t	=	t	input	1	2025-06-15 07:13:31.285	1	2025-06-15 07:13:31.285	0
1180	71	model_type_id	BIGINT	模型类型ID	f	f	6	Long	modelTypeId		21935	t	t	t	=	t	input	1	2025-06-15 07:13:31.335	1	2025-06-15 07:13:31.335	0
1181	71	publish_status	SMALLINT	发布状态[0:待审核,1:未发布,2:已发布]	f	f	7	Short	publishStatus		1	t	t	t	=	t	radio	1	2025-06-15 07:13:31.404	1	2025-06-15 07:13:31.404	0
1182	71	edge_platform	VARCHAR	边缘平台	t	f	8	String	edgePlatform		\N	t	t	t	=	t	input	1	2025-06-15 07:13:31.444	1	2025-06-15 07:13:31.444	0
1183	71	chip_model	VARCHAR	芯片型号	t	f	9	String	chipModel		\N	t	t	t	=	t	input	1	2025-06-15 07:13:31.495	1	2025-06-15 07:13:31.495	0
1184	71	run_environment	VARCHAR	运行时环境	t	f	10	String	runEnvironment		\N	t	t	t	=	t	input	1	2025-06-15 07:13:31.545	1	2025-06-15 07:13:31.545	0
1185	71	dev_language	VARCHAR	开发语言	t	f	11	String	devLanguage		\N	t	t	t	=	t	input	1	2025-06-15 07:13:31.595	1	2025-06-15 07:13:31.595	0
1186	71	git_url	VARCHAR	git地址	t	f	12	String	gitUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:31.644	1	2025-06-15 07:13:31.644	0
1187	71	pt_model_url	VARCHAR	pt模型地址	t	f	13	String	ptModelUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:31.684	1	2025-06-15 07:13:31.684	0
1188	71	pt_result_url	VARCHAR	pt模型训练结果地址	t	f	14	String	ptResultUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:31.724	1	2025-06-15 07:13:31.724	0
1189	71	onnx_model_url	VARCHAR	onnx模型地址	t	f	15	String	onnxModelUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:31.76	1	2025-06-15 07:13:31.76	0
1190	71	onnx_result_url	VARCHAR	onnx模型训练结果地址	t	f	16	String	onnxResultUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:31.804	1	2025-06-15 07:13:31.804	0
1191	71	rknn_model_url	VARCHAR	rknn模型地址	t	f	17	String	rknnModelUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:31.844	1	2025-06-15 07:13:31.844	0
1192	71	rknn_result_url	VARCHAR	rknn模型训练结果地址	t	f	18	String	rknnResultUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:31.884	1	2025-06-15 07:13:31.884	0
1193	72	id	BIGINT	主键ID	f	t	1	Long	id		644	f	t	f	=	t	input	1	2025-06-15 07:13:33.295	1	2025-06-15 07:13:33.295	0
1194	72	name	VARCHAR	模型服务名称	f	f	2	String	name		张三	t	t	t	LIKE	t	input	1	2025-06-15 07:13:33.355	1	2025-06-15 07:13:33.355	0
1195	72	model_id	BIGINT	模型ID	f	f	3	Long	modelId		20545	t	t	t	=	t	input	1	2025-06-15 07:13:33.414	1	2025-06-15 07:13:33.414	0
1196	72	dataset_id	BIGINT	数据集ID	t	f	4	Long	datasetId		7547	t	t	t	=	t	input	1	2025-06-15 07:13:33.455	1	2025-06-15 07:13:33.455	0
1197	72	publish_status	SMALLINT	发布状态[0:待发布,1:已发布,2:发布失败,3:已下架]	f	f	5	Short	publishStatus		1	t	t	t	=	t	radio	1	2025-06-15 07:13:33.495	1	2025-06-15 07:13:33.495	0
1198	72	audit_user_id	BIGINT	审核人ID	t	f	6	Long	auditUserId		29565	t	t	t	=	t	input	1	2025-06-15 07:13:33.535	1	2025-06-15 07:13:33.535	0
1199	72	version	VARCHAR	版本号	t	f	7	String	version		\N	t	t	t	=	t	input	1	2025-06-15 07:13:33.575	1	2025-06-15 07:13:33.575	0
1200	72	server_address	VARCHAR	服务访问地址	t	f	8	String	serverAddress		\N	t	t	t	=	t	input	1	2025-06-15 07:13:33.619	1	2025-06-15 07:13:33.619	0
1201	72	onnx_file_url	VARCHAR	ONNX模型文件	t	f	9	String	onnxFileUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:33.655	1	2025-06-15 07:13:33.655	0
1202	72	description	VARCHAR	描述	t	f	10	String	description		你说的对	t	t	t	=	t	editor	1	2025-06-15 07:13:33.725	1	2025-06-15 07:13:33.725	0
1203	72	size	BIGINT	模型大小	t	f	11	Long	size		\N	t	t	t	=	t	input	1	2025-06-15 07:13:33.775	1	2025-06-15 07:13:33.775	0
1204	72	execute_status	SMALLINT	执行状态[0:未启动,1:部署中,2:部署失败,3:运行中,4:已关闭]	f	f	12	Short	executeStatus		1	t	t	t	=	t	radio	1	2025-06-15 07:13:33.819	1	2025-06-15 07:13:33.819	0
1205	72	publish_time	TIMESTAMP	发布时间	t	f	13	LocalDateTime	publishTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-06-15 07:13:33.908	1	2025-06-15 07:13:33.908	0
1206	72	anchors_file_url	VARCHAR	anchors.txt文件	t	f	14	String	anchorsFileUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:33.965	1	2025-06-15 07:13:33.965	0
1207	72	apply_file_url	VARCHAR	完整应用文件（或量化后应用文件）	t	f	15	String	applyFileUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:34.024	1	2025-06-15 07:13:34.024	0
1208	72	apply_file_size	BIGINT	应用文件大小	t	f	16	Long	applyFileSize		\N	t	t	t	=	t	input	1	2025-06-15 07:13:34.064	1	2025-06-15 07:13:34.064	0
1209	72	apply_file_md5	VARCHAR	应用文件MD5值	t	f	17	String	applyFileMd5		\N	t	t	t	=	t	input	1	2025-06-15 07:13:34.103	1	2025-06-15 07:13:34.103	0
1210	73	id	BIGINT	主键ID	f	t	1	Long	id		26697	f	t	f	=	t	input	1	2025-06-15 07:13:35.726	1	2025-06-15 07:13:35.726	0
1211	73	model_serve_id	BIGINT	模型服务ID	f	f	2	Long	modelServeId		25650	t	t	t	=	t	input	1	2025-06-15 07:13:35.784	1	2025-06-15 07:13:35.784	0
1212	73	model_id	BIGINT	模型ID	t	f	3	Long	modelId		6210	t	t	t	=	t	input	1	2025-06-15 07:13:35.819	1	2025-06-15 07:13:35.819	0
1213	73	model_type_id	BIGINT	模型类型ID	t	f	4	Long	modelTypeId		17087	t	t	t	=	t	input	1	2025-06-15 07:13:35.854	1	2025-06-15 07:13:35.854	0
1214	73	version	VARCHAR	量化服务版本号	t	f	5	String	version		\N	t	t	t	=	t	input	1	2025-06-15 07:13:35.894	1	2025-06-15 07:13:35.894	0
1215	73	quantify_time	TIMESTAMP	量化时间	t	f	6	LocalDateTime	quantifyTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-06-15 07:13:35.934	1	2025-06-15 07:13:35.934	0
1216	73	quantify_state	SMALLINT	量化状态[0:量化未开始,1:量化中,2:量化成功,3:量化失败]	f	f	7	Short	quantifyState		\N	t	t	t	=	t	input	1	2025-06-15 07:13:35.975	1	2025-06-15 07:13:35.975	0
1217	73	pack_state	SMALLINT	打包状态[0:打包未开始,1:打包中,2:打包成功,3:打包失败]	f	f	8	Short	packState		\N	t	t	t	=	t	input	1	2025-06-15 07:13:36.015	1	2025-06-15 07:13:36.015	0
1218	73	quantify_file_url	VARCHAR	量化文件地址	t	f	9	String	quantifyFileUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:36.059	1	2025-06-15 07:13:36.059	0
1219	73	edge_platform	VARCHAR	边缘平台	t	f	10	String	edgePlatform		\N	t	t	t	=	t	input	1	2025-06-15 07:13:36.124	1	2025-06-15 07:13:36.124	0
1220	73	chip_model	VARCHAR	芯片型号	t	f	11	String	chipModel		\N	t	t	t	=	t	input	1	2025-06-15 07:13:36.164	1	2025-06-15 07:13:36.164	0
1221	73	run_environment	VARCHAR	运行时环境	t	f	12	String	runEnvironment		\N	t	t	t	=	t	input	1	2025-06-15 07:13:36.204	1	2025-06-15 07:13:36.204	0
1222	73	dev_language	VARCHAR	开发语言	t	f	13	String	devLanguage		\N	t	t	t	=	t	input	1	2025-06-15 07:13:36.245	1	2025-06-15 07:13:36.245	0
1223	73	apply_type	SMALLINT	部署类型[0:压缩包下载,1:gitee拉取]	t	f	14	Short	applyType		1	t	t	t	=	t	select	1	2025-06-15 07:13:36.284	1	2025-06-15 07:13:36.284	0
1224	73	gitee_url	VARCHAR	下载或拉取地址	t	f	15	String	giteeUrl		https://www.iocoder.cn	t	t	t	=	t	input	1	2025-06-15 07:13:36.334	1	2025-06-15 07:13:36.334	0
1225	73	reason	VARCHAR	失败原因	t	f	16	String	reason		不香	t	t	t	=	t	input	1	2025-06-15 07:13:36.369	1	2025-06-15 07:13:36.369	0
1226	73	quantify_description	VARCHAR	量化备注	t	f	17	String	quantifyDescription		你说的对	t	t	t	=	t	editor	1	2025-06-15 07:13:36.414	1	2025-06-15 07:13:36.414	0
1227	73	rknn_dir	VARCHAR	rknn存放目录	t	f	18	String	rknnDir		\N	t	t	t	=	t	input	1	2025-06-15 07:13:36.45	1	2025-06-15 07:13:36.45	0
1228	74	id	BIGINT	主键ID	f	t	1	Long	id		114	f	t	f	=	t	input	1	2025-06-15 07:13:51.935	1	2025-06-15 07:13:51.935	0
1229	74	model_serve_id	BIGINT	模型服务ID	f	f	2	Long	modelServeId		29653	t	t	t	=	t	input	1	2025-06-15 07:13:52.005	1	2025-06-15 07:13:52.005	0
1230	74	model_id	BIGINT	模型ID	t	f	3	Long	modelId		29427	t	t	t	=	t	input	1	2025-06-15 07:13:52.054	1	2025-06-15 07:13:52.054	0
1231	74	name	VARCHAR	测试模型服务名称	f	f	4	String	name		张三	t	t	t	LIKE	t	input	1	2025-06-15 07:13:52.104	1	2025-06-15 07:13:52.104	0
1232	74	type	SMALLINT	测试模型服务类型[0:图片测试,1:视频测试]	f	f	5	Short	type		1	t	t	t	=	t	select	1	2025-06-15 07:13:52.155	1	2025-06-15 07:13:52.155	0
1233	74	planned_quantity	INTEGER	计划标注数量	t	f	6	Integer	plannedQuantity		\N	t	t	t	=	t	input	1	2025-06-15 07:13:52.205	1	2025-06-15 07:13:52.205	0
1234	74	marked_quantity	INTEGER	已经标注数量	t	f	7	Integer	markedQuantity		\N	t	t	t	=	t	input	1	2025-06-15 07:13:52.264	1	2025-06-15 07:13:52.264	0
1235	74	not_target_count	INTEGER	无目标数量	t	f	8	Integer	notTargetCount		26255	t	t	t	=	t	input	1	2025-06-15 07:13:52.299	1	2025-06-15 07:13:52.299	0
1236	74	finish_time	TIMESTAMP	完成时间	t	f	9	LocalDateTime	finishTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-06-15 07:13:52.374	1	2025-06-15 07:13:52.374	0
1237	74	state	SMALLINT	服务状态[0:进行中,1:成功,2:失败]	f	f	10	Short	state		\N	t	t	t	=	t	input	1	2025-06-15 07:13:52.415	1	2025-06-15 07:13:52.415	0
1238	74	description	VARCHAR	描述	t	f	11	String	description		你说的对	t	t	t	=	t	editor	1	2025-06-15 07:13:52.454	1	2025-06-15 07:13:52.454	0
1239	75	id	BIGINT	主键ID	f	t	1	Long	id		7333	f	t	f	=	t	input	1	2025-06-15 07:13:53.94	1	2025-06-15 07:13:53.94	0
1240	75	model_server_test_id	BIGINT	测试模型服务ID	f	f	2	Long	modelServerTestId		25492	t	t	t	=	t	input	1	2025-06-15 07:13:53.99	1	2025-06-15 07:13:53.99	0
1241	75	model_id	BIGINT	模型ID	t	f	3	Long	modelId		2434	t	t	t	=	t	input	1	2025-06-15 07:13:54.044	1	2025-06-15 07:13:54.044	0
1242	75	dataset_image_id	BIGINT	数据集图片ID	f	f	4	Long	datasetImageId		9923	t	t	t	=	t	input	1	2025-06-15 07:13:54.079	1	2025-06-15 07:13:54.079	0
1243	75	anno_time	TIMESTAMP	标注时间	t	f	5	LocalDateTime	annoTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-06-15 07:13:54.124	1	2025-06-15 07:13:54.124	0
1244	75	anno_image_path	VARCHAR	标注后图片路径	f	f	6	String	annoImagePath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:54.174	1	2025-06-15 07:13:54.174	0
1245	75	select_count	INTEGER	选取次数	t	f	7	Integer	selectCount		23116	t	t	t	=	t	input	1	2025-06-15 07:13:54.209	1	2025-06-15 07:13:54.209	0
1246	76	id	BIGINT	主键ID	f	t	1	Long	id		12486	f	t	f	=	t	input	1	2025-06-15 07:13:55.545	1	2025-06-15 07:13:55.545	0
1247	76	model_server_test_id	BIGINT	测试模型服务ID	f	f	2	Long	modelServerTestId		19208	t	t	t	=	t	input	1	2025-06-15 07:13:55.605	1	2025-06-15 07:13:55.605	0
1248	76	model_id	BIGINT	模型ID	t	f	3	Long	modelId		27202	t	t	t	=	t	input	1	2025-06-15 07:13:55.645	1	2025-06-15 07:13:55.645	0
1249	76	dataset_video_id	BIGINT	数据集视频ID	f	f	4	Long	datasetVideoId		9487	t	t	t	=	t	input	1	2025-06-15 07:13:55.684	1	2025-06-15 07:13:55.684	0
1250	76	anno_time	TIMESTAMP	标注时间	t	f	5	LocalDateTime	annoTime		\N	t	t	t	BETWEEN	t	datetime	1	2025-06-15 07:13:55.724	1	2025-06-15 07:13:55.724	0
1251	76	anno_video_path	VARCHAR	标注后视频路径	f	f	6	String	annoVideoPath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:55.764	1	2025-06-15 07:13:55.764	0
1252	76	state	SMALLINT	状态[0:运行中,1:成功,2:失败]	f	f	7	Short	state		\N	t	t	t	=	t	input	1	2025-06-15 07:13:55.805	1	2025-06-15 07:13:55.805	0
1253	77	id	BIGINT	主键ID	f	t	1	Long	id		26586	f	t	f	=	t	input	1	2025-06-15 07:13:57.205	1	2025-06-15 07:13:57.205	0
1254	77	model_server_id	BIGINT	模型服务ID	f	f	2	Long	modelServerId		11393	t	t	t	=	t	input	1	2025-06-15 07:13:57.265	1	2025-06-15 07:13:57.265	0
1255	77	model_id	BIGINT	模型ID	t	f	3	Long	modelId		900	t	t	t	=	t	input	1	2025-06-15 07:13:57.324	1	2025-06-15 07:13:57.324	0
1256	77	video_path	VARCHAR	视频地址	f	f	4	String	videoPath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:57.359	1	2025-06-15 07:13:57.359	0
1257	77	anno_video_path	VARCHAR	标注视频地址	t	f	5	String	annoVideoPath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:57.404	1	2025-06-15 07:13:57.404	0
1258	77	cover_path	VARCHAR	封面地址	t	f	6	String	coverPath		\N	t	t	t	=	t	input	1	2025-06-15 07:13:57.439	1	2025-06-15 07:13:57.439	0
1259	77	description	VARCHAR	描述	t	f	7	String	description		你说的对	t	t	t	=	t	editor	1	2025-06-15 07:13:57.484	1	2025-06-15 07:13:57.484	0
1260	77	video_resolution	VARCHAR	视频分辨率	t	f	8	String	videoResolution		\N	t	t	t	=	t	input	1	2025-06-15 07:13:57.524	1	2025-06-15 07:13:57.524	0
1261	77	duration	INTEGER	视频时长	t	f	9	Integer	duration		\N	t	t	t	=	t	input	1	2025-06-15 07:13:57.564	1	2025-06-15 07:13:57.564	0
1262	77	suffix	VARCHAR	视频后缀	t	f	10	String	suffix		\N	t	t	t	=	t	input	1	2025-06-15 07:13:57.614	1	2025-06-15 07:13:57.614	0
1263	77	file_size	BIGINT	视频文件大小	t	f	11	Long	fileSize		\N	t	t	t	=	t	input	1	2025-06-15 07:13:57.664	1	2025-06-15 07:13:57.664	0
1264	78	id	BIGINT	主键ID	f	t	1	Long	id		21910	f	t	f	=	t	input	1	2025-06-15 07:13:59.125	1	2025-06-15 07:13:59.125	0
1265	78	name	VARCHAR	名称	f	f	2	String	name		李四	t	t	t	LIKE	t	input	1	2025-06-15 07:13:59.165	1	2025-06-15 07:13:59.165	0
1266	78	type	SMALLINT	模型类型分类[0:模型分类,1:行业分类,2:运行环境]	f	f	3	Short	type		1	t	t	t	=	t	select	1	2025-06-15 07:13:59.204	1	2025-06-15 07:13:59.204	0
1267	78	parent_id	BIGINT	父ID	t	f	4	Long	parentId		12831	t	t	t	=	t	input	1	2025-06-15 07:13:59.244	1	2025-06-15 07:13:59.244	0
1268	79	id	BIGINT	主键ID	f	t	1	Long	id		22338	f	t	f	=	t	input	1	2025-06-15 07:14:00.525	1	2025-06-15 07:14:00.525	0
1269	79	name	VARCHAR	仓库名称	f	f	2	String	name		赵六	t	t	t	LIKE	t	input	1	2025-06-15 07:14:00.587	1	2025-06-15 07:14:00.587	0
1270	79	cover_path	VARCHAR	封面地址	t	f	3	String	coverPath		\N	t	t	t	=	t	input	1	2025-06-15 07:14:00.629	1	2025-06-15 07:14:00.629	0
1271	79	description	VARCHAR	描述	t	f	4	String	description		你说的对	t	t	t	=	t	editor	1	2025-06-15 07:14:00.664	1	2025-06-15 07:14:00.664	0
1272	80	id	BIGINT	主键ID	f	t	1	Long	id		17660	f	t	f	=	t	input	1	2025-06-15 07:14:01.985	1	2025-08-13 09:36:53.952	0
1273	80	dataset_id	BIGINT	数据集ID	f	f	2	Long	datasetId		32591	t	t	t	=	t	input	1	2025-06-15 07:14:02.055	1	2025-08-13 09:36:53.967	0
1274	80	warehouse_id	BIGINT	数据仓ID	f	f	3	Long	warehouseId		30097	t	t	t	=	t	input	1	2025-06-15 07:14:02.104	1	2025-08-13 09:36:53.974	0
1275	80	plan_sync_count	INTEGER	计划同步数量	f	f	4	Integer	planSyncCount		755	t	t	t	=	t	input	1	2025-06-15 07:14:02.139	1	2025-08-13 09:36:53.981	0
1276	80	sync_count	INTEGER	已同步数量	f	f	5	Integer	syncCount		4341	t	t	t	=	t	input	1	2025-06-15 07:14:02.174	1	2025-08-13 09:36:53.988	0
1277	80	sync_status	SMALLINT	同步状态[0:未同步,1:同步中,2:同步完成]	f	f	6	Short	syncStatus		2	t	t	t	=	t	radio	1	2025-06-15 07:14:02.209	1	2025-08-13 09:36:54.003	0
1278	80	fail_count	INTEGER	同步失败数量	f	f	7	Integer	failCount		11526	t	t	t	=	t	input	1	2025-06-15 07:14:02.255	1	2025-08-13 09:36:54.013	0
843	50	app_id	VARCHAR	应用ID	f	f	2	String	appId		17316	t	t	t	=	t	input	1	2024-07-10 18:03:42.308	1	2025-08-13 13:37:21.86	0
844	50	rule_code	VARCHAR	规则标识	f	f	3	String	ruleCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:42.319	1	2025-08-13 13:37:21.875	0
845	50	rule_name	VARCHAR	规则名称	f	f	4	String	ruleName		李四	t	t	t	LIKE	t	input	1	2024-07-10 18:03:42.33	1	2025-08-13 13:37:21.89	0
846	50	job_code	VARCHAR	任务标识	f	f	5	String	jobCode		\N	t	t	t	=	t	input	1	2024-07-10 18:03:42.34	1	2025-08-13 13:37:21.904	0
847	50	status	VARCHAR	状态(字典值：0启用  1停用)	f	f	6	String	status		2	t	t	t	=	t	radio	1	2024-07-10 18:03:42.35	1	2025-08-13 13:37:21.92	0
848	50	triggering	SMALLINT	触发机制（0:全部，1:任意一个）	t	f	7	Short	triggering		\N	t	t	t	=	t	input	1	2024-07-10 18:03:42.364	1	2025-08-13 13:37:21.936	0
849	50	remark	VARCHAR	规则描述，可以为空	t	f	8	String	remark		你说的对	t	t	t	=	t	input	1	2024-07-10 18:03:42.382	1	2025-08-13 13:37:21.951	0
850	50	create_by	VARCHAR	创建人	t	f	9	String	createBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:42.393	1	2025-08-13 13:37:21.966	0
851	50	create_time	TIMESTAMP	创建时间	f	f	10	LocalDateTime	createTime		\N	f	f	t	BETWEEN	t	datetime	1	2024-07-10 18:03:42.417	1	2025-08-13 13:37:21.988	0
852	50	update_by	VARCHAR	更新人	t	f	11	String	updateBy		\N	t	t	t	=	t	input	1	2024-07-10 18:03:42.55	1	2025-08-13 13:37:22.004	0
853	50	update_time	TIMESTAMP	更新时间	f	f	12	LocalDateTime	updateTime		\N	f	f	f	BETWEEN	f	datetime	1	2024-07-10 18:03:43.001	1	2025-08-13 13:37:22.02	0
854	50	tenant_id	BIGINT	租户ID	t	f	13	Long	tenantId		31299	f	f	f	=	f	input	1	2024-07-10 18:03:43.164	1	2025-08-13 13:37:22.026	0
\.


--
-- Data for Name: infra_codegen_table; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_codegen_table (id, data_source_config_id, scene, table_name, table_comment, remark, module_name, business_name, class_name, class_comment, author, template_type, front_type, parent_menu_id, master_table_id, sub_join_column_id, sub_join_many, tree_parent_column_id, tree_name_column_id, creator, create_time, updater, update_time, deleted) FROM stdin;
1	0	1	infra_job	定时任务表	\N	infra	job	Job	定时任务	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-09 11:38:00.076	1	2024-07-09 11:38:00.076	1
20	1	1	device	设备表	\N	device			设备	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:30.81	1	2024-07-10 10:33:30.81	1
27	1	1	rule_conditions	规则条件表	\N	rule	conditions	Conditions	规则条件	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:38.948	1	2024-07-10 10:33:38.948	1
2	1	1	device	设备表	\N	device			设备	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:32:59.118	1	2024-07-10 10:32:59.118	1
28	1	1	device	设备表	\N	device			设备	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 17:46:57.377	1	2024-07-10 17:46:57.377	1
30	1	1	device	设备表	\N	device			设备	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 17:47:44.046	1	2024-07-10 17:47:44.046	1
23	1	1	device_group	设备分组表	\N	device	group	Group	设备分组	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:35.441	1	2024-07-10 10:33:35.441	1
22	1	1	rule_alarm	规则告警表	\N	rule	alarm	Alarm	规则告警	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:35.183	1	2024-07-10 10:33:35.183	1
29	1	1	rule_conditions	规则条件表	\N	rule	conditions	Conditions	规则条件	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 17:47:27.332	1	2024-07-10 17:47:27.332	1
31	1	1	device	设备表	设备表	device	device	Device	设备	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 17:52:39.556	1	2024-07-10 17:56:36.073	1
26	1	1	device_log	设备日志表	\N	device	log	Log	设备日志	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:36.659	1	2024-07-10 10:33:36.659	1
25	1	1	rule_alarm_list	告警列表	\N	rule	alarmlist	AlarmList	告警列	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:36.436	1	2024-07-10 10:33:36.436	1
21	1	1	rule	规则信息表	\N	rule			规则信息	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:33.308	1	2024-07-10 10:33:33.308	1
19	1	1	protocol	协议信息表	\N	protocol			协议信息	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:29.336	1	2024-07-10 10:33:29.336	1
18	1	1	product_type	产品分类表	\N	product	type	Type	产品分类	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:27.987	1	2024-07-10 10:33:27.987	1
17	1	1	product_template	产品模板表	\N	product	template	Template	产品模板	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:26.741	1	2024-07-10 10:33:26.741	1
16	1	1	product_services	产品模型服务表	\N	product	services	Services	产品模型服务	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:25.075	1	2024-07-10 10:33:25.075	1
15	1	1	product_properties	产品模型属性表	\N	product	properties	Properties	产品模型属性	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:23.512	1	2024-07-10 10:33:23.512	1
14	1	1	product_event_response	产品模型事件响应表	\N	product	eventresponse	EventResponse	产品模型事件响应	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:22.15	1	2024-07-10 10:33:22.15	1
13	1	1	product_event	产品模型事件表	\N	product	event	Event	产品模型事件	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:19.988	1	2024-07-10 10:33:19.988	1
12	1	1	product_commands_response	产品模型设备响应服务命令属性表	\N	product	commandsresponse	CommandsResponse	产品模型设备响应服务命令属性	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:18.637	1	2024-07-10 10:33:18.637	1
11	1	1	product_commands_requests	产品模型设备下发服务命令属性表	\N	product	commandsrequests	CommandsRequests	产品模型设备下发服务命令属性	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:16.828	1	2024-07-10 10:33:16.828	1
10	1	1	product_commands	产品模型设备服务命令表	\N	product	commands	Commands	产品模型设备服务命令	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:14.952	1	2024-07-10 10:33:14.952	1
9	1	1	product	产品表	\N	product			产品	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:12.643	1	2024-07-10 10:33:12.643	1
8	1	1	ota_tasks	OTA升级任务表	\N	ota	tasks	Tasks	OTA升级任务	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:10.632	1	2024-07-10 10:33:10.632	1
7	1	1	ota_records	OTA升级记录表	\N	ota	records	Records	OTA升级记录	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:08.997	1	2024-07-10 10:33:08.997	1
6	1	1	ota_packages	OTA升级包表	\N	ota	packages	Packages	OTA升级包	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:07.359	1	2024-07-10 10:33:07.359	1
5	1	1	device_topic	设备Topic数据表	\N	device	topic	Topic	设备Topic数据	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:05.995	1	2024-07-10 10:33:05.995	1
4	1	1	device_log	设备日志表	\N	device	log	Log	设备日志	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:03.425	1	2024-07-10 10:33:03.425	1
3	1	1	device_group	设备分组表	\N	device	group	Group	设备分组	芋道源码	1	10	\N	\N	\N	\N	\N	\N	1	2024-07-10 10:33:02.108	1	2024-07-10 10:33:02.108	1
43	1	1	product_event	产品模型事件表	产品模型事件	xxx	product_event	ProductEvent	产品模型事件	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:35.299	1	2024-07-10 18:41:25.407	0
42	1	1	product_commands_response	产品模型设备响应服务命令属性表	产品模型设备响应服务命令属性	xxx	product_commands_response	ProductCommandsResponse	产品模型设备响应服务命令属性	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:34.203	1	2024-07-10 18:41:33.071	0
40	1	1	product_commands	产品模型设备服务命令表	产品模型设备服务命令	xxx	product_commands	ProductCommands	产品模型设备服务命令	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:31.723	1	2024-07-10 18:41:48.682	0
39	1	1	product	产品表	产品	xxx	product	Product	产品	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:30.383	1	2024-07-10 18:41:58.202	0
38	1	1	ota_tasks	OTA升级任务表	OTA升级任务	xxx	ota_tasks	OtaTasks	OTA升级任务	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:29.415	1	2024-07-10 18:42:05.908	0
37	1	1	ota_records	OTA升级记录表	OTA升级记录	xxx	ota_records	OtaRecords	OTA升级记录	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:28.484	1	2024-07-10 18:42:13.331	0
36	1	1	ota_packages	OTA升级包表	OTA升级包	xxx	ota_packages	OtaPackages	OTA升级包	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:27.261	1	2024-07-10 18:42:24.741	0
35	1	1	device_topic	设备Topic数据表	设备Topic数据	xxx	device_topic	DeviceTopic	设备Topic数据	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:26.317	1	2024-07-10 18:42:31.727	0
34	1	1	device_log	设备日志表	设备日志	xxx	device_log	DeviceLog	设备日志	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:25.228	1	2024-07-10 18:42:41.341	0
32	1	1	device	设备表	设备	xxx	device	Device	设备	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:21.616	1	2024-07-10 18:42:58.366	0
51	1	1	rule_alarm	规则告警表	规则告警	xxx	rule_alarm	RuleAlarm	规则告警	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:43.795	1	2024-07-10 18:40:02.672	0
52	1	1	rule_alarm_list	告警列表	告警规则列	xxx	rule_alarm_list	RuleAlarmList	告警规则列	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:44.706	1	2024-07-10 18:40:10.077	0
53	1	1	rule_conditions	规则条件表	规则条件	xxx	rule_conditions	RuleConditions	规则条件	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:45.717	1	2024-07-10 18:40:17.361	0
49	1	1	protocol	协议信息表	协议信息	xxx	protocol	Protocol	协议信息	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:41.245	1	2024-07-10 18:40:26.385	0
48	1	1	product_type	产品分类表	产品分类	xxx	product_type	ProductType	产品分类	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:40.4	1	2024-07-10 18:40:34.837	0
47	1	1	product_template	产品模板表	产品模板	xxx	product_template	ProductTemplate	产品模板	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:39.324	1	2024-07-10 18:40:45.974	0
46	1	1	product_services	产品模型服务表	产品模型服务	xxx	product_services	ProductServices	产品模型服务	芋道源码	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:38.358	1	2024-07-10 18:40:55.747	0
45	1	1	product_properties	产品模型属性表	产品模型属性	xxx	product_properties	ProductProperties	产品模型属性	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:37.392	1	2024-07-10 18:41:06.348	0
44	1	1	product_event_response	产品模型事件响应表	产品模型事件响应	xxx	product_event_response	ProductEventResponse	产品模型事件响应	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:36.34	1	2024-07-10 18:41:15.802	0
41	1	1	product_commands_requests	产品模型设备下发服务命令属性表	产品模型设备下发服务命令属性	xxx	product_commands_requests	ProductCommandsRequests	产品模型设备下发服务命令属性	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:32.84	1	2024-07-10 18:41:41.652	0
33	1	1	device_group	设备分组表	设备分组	xxx	device_group	DeviceGroup	设备分组	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:24.398	1	2024-07-10 18:42:49.806	0
54	1	1	video_snap_space	抓拍空间表	\N	video	snapspace	SnapSpace	抓拍空间	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2024-12-26 14:46:24.623	1	2024-12-26 14:46:24.623	0
55	1	1	video_snap_job	抓拍任务表	\N	video	snapjob	SnapJob	抓拍任务	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2024-12-26 14:46:26.456	1	2024-12-26 14:46:26.456	0
56	1	1	algorithm_alarm_data	计算告警数据表	\N	algorithm	alarmdata	AlarmData	计算告警数据	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-02-25 09:34:34.247	1	2025-02-25 09:34:34.247	0
57	1	1	algorithm_customer	计算客户表	\N	algorithm	customer	Customer	计算客户	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-02-25 09:34:35.376	1	2025-02-25 09:34:35.376	0
58	1	1	algorithm_model	计算模型表	\N	algorithm	model	Model	计算模型	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-02-25 09:34:36.234	1	2025-02-25 09:34:36.234	0
59	1	1	algorithm_task	计算任务表	\N	algorithm	task	Task	计算任务	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-02-25 09:34:37.123	1	2025-02-25 09:34:37.123	0
60	1	1	algorithm_push_log	计算推送日志表	\N	algorithm	pushlog	PushLog	计算推送日志	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-02-25 11:49:53.993	1	2025-02-25 11:49:53.993	0
61	1	1	algorithm_playback	计算录像回放表	\N	algorithm	playback	Playback	计算录像回放	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-03-31 16:12:44.158	1	2025-03-31 16:12:44.158	0
62	1	1	algorithm_video	计算视频设备表	\N	algorithm	video	Video	计算视频设备	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-03-31 16:12:44.828	1	2025-03-31 16:12:44.828	0
63	1	1	algorithm_nvr	计算NVR表	\N	algorithm	nvr	Nvr	计算NVR	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-03-31 18:02:01.952	1	2025-03-31 18:02:01.952	1
64	3	1	dataset	数据集表	\N	dataset			数据集	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:17.224	1	2025-06-15 07:13:17.224	0
65	3	1	dataset_image	图片数据集表	\N	dataset	image	Image	图片数据集	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:19.295	1	2025-06-15 07:13:19.295	0
66	3	1	dataset_tag	数据集标签表	\N	dataset	tag	Tag	数据集标签	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:21.335	1	2025-06-15 07:13:21.335	0
67	3	1	dataset_task	标注任务表	\N	dataset	task	Task	标注任务	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:23.265	1	2025-06-15 07:13:23.265	0
68	3	1	dataset_task_result	标注任务结果表	\N	dataset	taskresult	TaskResult	标注任务结果	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:25.935	1	2025-06-15 07:13:25.935	0
69	3	1	dataset_task_user	标注任务用户表	\N	dataset	taskuser	TaskUser	标注任务用户	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:28.005	1	2025-06-15 07:13:28.005	0
70	3	1	dataset_video	视频数据集表	\N	dataset	video	Video	视频数据集	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:29.379	1	2025-06-15 07:13:29.379	0
71	3	1	model	模型表	\N	model			模型	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:30.964	1	2025-06-15 07:13:30.964	0
72	3	1	model_server	模型服务表	\N	model	server	Server	模型服务	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:33.205	1	2025-06-15 07:13:33.205	0
73	3	1	model_server_quantify	模型量化服务表	\N	model	serverquantify	ServerQuantify	模型量化服务	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:35.625	1	2025-06-15 07:13:35.625	0
74	3	1	model_server_test	模型测试服务表	\N	model	servertest	ServerTest	模型测试服务	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:51.835	1	2025-06-15 07:13:51.835	0
75	3	1	model_server_test_image	模型测试图片表	\N	model	servertestimage	ServerTestImage	模型测试图片	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:53.845	1	2025-06-15 07:13:53.845	0
76	3	1	model_server_test_video	模型测试视频表	\N	model	servertestvideo	ServerTestVideo	模型测试视频	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:55.455	1	2025-06-15 07:13:55.455	0
77	3	1	model_server_video	模型服务视频表	\N	model	servervideo	ServerVideo	模型服务视频	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:57.094	1	2025-06-15 07:13:57.094	0
78	3	1	model_type	模型类型表	\N	model	type	Type	模型类型	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:13:59.015	1	2025-06-15 07:13:59.015	0
79	3	1	warehouse	数据仓表	\N	warehouse			数据仓	IoT	1	30	\N	\N	\N	\N	\N	\N	1	2025-06-15 07:14:00.405	1	2025-06-15 07:14:00.405	0
80	3	1	warehouse_dataset	数据仓数据集关联表	\N	warehouse	dataset	Dataset	数据仓数据集关联	IoT	1	30	1237	\N	\N	\N	\N	\N	1	2025-06-15 07:14:01.885	1	2025-08-13 09:36:53.933	0
50	1	1	rule	规则信息表	规则信息	xxx	rule	Rule	规则信息	BasicLab	1	30	2761	\N	\N	\N	\N	\N	1	2024-07-10 18:03:42.255	1	2025-08-13 13:37:21.821	0
\.


--
-- Data for Name: infra_config; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_config (id, category, type, name, config_key, value, visible, remark, creator, create_time, updater, update_time, deleted) FROM stdin;
2	biz	1	用户管理-账号初始密码	sys.user.init-password	123456	f	初始化密码 123456	admin	2021-01-05 17:03:48	1	2024-04-03 17:22:28	0
7	url	2	MySQL 监控的地址	url.druid		t		1	2023-04-07 13:41:16	1	2023-04-07 14:33:38	0
8	url	2	SkyWalking 监控的地址	url.skywalking		t		1	2023-04-07 13:41:16	1	2023-04-07 14:57:03	0
9	url	2	Spring Boot Admin 监控的地址	url.spring-boot-admin		t		1	2023-04-07 13:41:16	1	2023-04-07 14:52:07	0
10	url	2	Swagger 接口文档的地址	url.swagger		t		1	2023-04-07 13:41:16	1	2023-04-07 14:59:00	0
11	ui	2	腾讯地图 key	tencent.lbs.key	TVDBZ-TDILD-4ON4B-PFDZA-RNLKH-VVF6E	t	腾讯地图 key	1	2023-06-03 19:16:27	1	2023-06-03 19:16:27	0
12	test2	2	test3	test4	test5	t	test6	1	2023-12-03 09:55:16	1	2023-12-03 09:55:27	0
\.


--
-- Data for Name: infra_data_source_config; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_data_source_config (id, name, url, username, password, creator, create_time, updater, update_time, deleted) FROM stdin;
1	iot-device	jdbc:postgresql://iot.basiclab.top:5432/iot-device5?autoReconnect=true&autoReconnectForPools=true&useUnicode=true&characterEncoding=utf8&createDatabaseIfNotExist=true&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull&stringtype=unspecified	postgres	zqUjuSs90YBa0YHdYTUtcpGy9Adt07GcDin+ZhRV/GI=	1	2024-07-10 10:28:26.149	1	2024-12-26 14:45:33.98	0
2	iot-box	jdbc:postgresql://iot.basiclab.top:5432/dvm-box?autoReconnect=true&autoReconnectForPools=true&useUnicode=true&characterEncoding=utf8&createDatabaseIfNotExist=true&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull&stringtype=unspecified	postgres	zqUjuSs90YBa0YHdYTUtcpGy9Adt07GcDin+ZhRV/GI=	1	2025-04-07 09:18:06.079	1	2025-04-07 09:18:06.079	0
3	dataset	jdbc:postgresql://iot.basiclab.top:5432/dataset?autoReconnect=true&autoReconnectForPools=true&useUnicode=true&characterEncoding=utf8&createDatabaseIfNotExist=true&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull&stringtype=unspecified	postgres	zqUjuSs90YBa0YHdYTUtcpGy9Adt07GcDin+ZhRV/GI=	1	2025-06-15 06:46:39.595	1	2025-06-15 06:46:39.595	0
\.


--
-- Data for Name: infra_file; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_file (id, config_id, name, path, url, type, size, creator, create_time, updater, update_time, deleted) FROM stdin;
1	22	113dcbab48d22fc988151b98be2b8b4f262effdb5305c171cf1fe9a71801377a.png	113dcbab48d22fc988151b98be2b8b4f262effdb5305c171cf1fe9a71801377a.png	http://test.yudao.iocoder.cn/113dcbab48d22fc988151b98be2b8b4f262effdb5305c171cf1fe9a71801377a.png	image/png	677522	1	2024-07-24 10:44:19.797	1	2024-07-24 10:44:19.797	0
\.


--
-- Data for Name: infra_file_config; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_file_config (id, name, storage, remark, master, config, creator, create_time, updater, update_time, deleted) FROM stdin;
4	数据库	1	我是数据库	f	{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.db.DBFileClientConfig","domain":"http://127.0.0.1:48080"}	1	2022-03-15 23:56:24	1	2024-02-28 22:54:07	0
22	七牛存储器	20		t	{"@class":"cn.iocoder.yudao.module.infra.framework.file.core.client.s3.S3FileClientConfig","endpoint":"s3.cn-south-1.qiniucs.com","domain":"http://test.yudao.iocoder.cn","bucket":"ruoyi-vue-pro","accessKey":"3TvrJ70gl2Gt6IBe7_IZT1F6i_k0iMuRtyEv4EyS","accessSecret":"wd0tbVBYlp0S-ihA8Qg2hPLncoP83wyrIq24OZuY"}	1	2024-01-13 22:11:12	1	2024-04-03 19:38:34	0
\.


--
-- Data for Name: infra_file_content; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_file_content (id, config_id, path, content, creator, create_time, updater, update_time, deleted) FROM stdin;
\.


--
-- Data for Name: infra_job; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_job (id, name, status, handler_name, handler_param, cron_expression, retry_count, retry_interval, monitor_timeout, creator, create_time, updater, update_time, deleted) FROM stdin;
5	支付通知 Job	2	payNotifyJob	\N	* * * * * ?	0	0	0	1	2021-10-27 08:34:42	1	2023-07-09 20:51:41	0
17	支付订单同步 Job	2	payOrderSyncJob	\N	0 0/1 * * * ?	0	0	0	1	2023-07-22 14:36:26	1	2023-07-22 15:39:08	0
18	支付订单过期 Job	2	payOrderExpireJob	\N	0 0/1 * * * ?	0	0	0	1	2023-07-22 15:36:23	1	2023-07-22 15:39:54	0
19	退款订单的同步 Job	2	payRefundSyncJob	\N	0 0/1 * * * ?	0	0	0	1	2023-07-23 21:03:44	1	2023-07-23 21:09:00	0
21	交易订单的自动过期 Job	2	tradeOrderAutoCancelJob		0 * * * * ?	3	0	0	1	2023-09-25 23:43:26	1	2023-09-26 19:23:30	0
22	交易订单的自动收货 Job	2	tradeOrderAutoReceiveJob		0 * * * * ?	3	0	0	1	2023-09-26 19:23:53	1	2023-09-26 23:38:08	0
23	交易订单的自动评论 Job	2	tradeOrderAutoCommentJob		0 * * * * ?	3	0	0	1	2023-09-26 23:38:29	1	2023-09-27 11:03:10	0
24	佣金解冻 Job	2	brokerageRecordUnfreezeJob		0 * * * * ?	3	0	0	1	2023-09-28 22:01:46	1	2023-09-28 22:01:56	0
25	访问日志清理 Job	2	accessLogCleanJob		0 0 0 * * ?	3	0	0	1	2023-10-03 10:59:41	1	2023-10-03 11:01:10	0
26	错误日志清理 Job	2	errorLogCleanJob		0 0 0 * * ?	3	0	0	1	2023-10-03 11:00:43	1	2023-10-03 11:01:12	0
27	任务日志清理 Job	2	jobLogCleanJob		0 0 0 * * ?	3	0	0	1	2023-10-03 11:01:33	1	2023-10-03 11:01:42	0
\.


--
-- Data for Name: infra_job_log; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_job_log (id, job_id, handler_name, handler_param, execute_index, begin_time, end_time, duration, status, result, creator, create_time, updater, update_time, deleted) FROM stdin;
\.


--
-- Data for Name: infra_platform_branding; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.infra_platform_branding (id, platform_name, platform_logo_file_id, dashboard_title, login_name, login_logo_file_id, login_form_title, login_bg_light_file_id, login_bg_dark_file_id, default_platform_name, default_platform_logo_file_id, default_dashboard_title, default_login_name, default_login_logo_file_id, default_login_form_title, default_login_bg_light_file_id, default_login_bg_dark_file_id, creator, create_time, updater, update_time, deleted) FROM stdin;
\.


--
-- Data for Name: qrtz_blob_triggers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_blob_triggers (sched_name, trigger_name, trigger_group, blob_data) FROM stdin;
\.


--
-- Data for Name: qrtz_calendars; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_calendars (sched_name, calendar_name, calendar) FROM stdin;
\.


--
-- Data for Name: qrtz_cron_triggers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_cron_triggers (sched_name, trigger_name, trigger_group, cron_expression, time_zone_id) FROM stdin;
\.


--
-- Data for Name: qrtz_fired_triggers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_fired_triggers (sched_name, entry_id, trigger_name, trigger_group, instance_name, fired_time, sched_time, priority, state, job_name, job_group, is_nonconcurrent, requests_recovery) FROM stdin;
\.


--
-- Data for Name: qrtz_job_details; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_job_details (sched_name, job_name, job_group, description, job_class_name, is_durable, is_nonconcurrent, is_update_data, requests_recovery, job_data) FROM stdin;
\.


--
-- Data for Name: qrtz_locks; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_locks (sched_name, lock_name) FROM stdin;
schedulerName	STATE_ACCESS
schedulerName	TRIGGER_ACCESS
\.


--
-- Data for Name: qrtz_paused_trigger_grps; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_paused_trigger_grps (sched_name, trigger_group) FROM stdin;
\.


--
-- Data for Name: qrtz_scheduler_state; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_scheduler_state (sched_name, instance_name, last_checkin_time, checkin_interval) FROM stdin;
schedulerName	e2fae02ab02b1785826736637	1785827811496	15000
schedulerName	5fd4acd179ab1785826750306	1785827812929	15000
\.


--
-- Data for Name: qrtz_simple_triggers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_simple_triggers (sched_name, trigger_name, trigger_group, repeat_count, repeat_interval, times_triggered) FROM stdin;
\.


--
-- Data for Name: qrtz_simprop_triggers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_simprop_triggers (sched_name, trigger_name, trigger_group, str_prop_1, str_prop_2, str_prop_3, int_prop_1, int_prop_2, long_prop_1, long_prop_2, dec_prop_1, dec_prop_2, bool_prop_1, bool_prop_2) FROM stdin;
\.


--
-- Data for Name: qrtz_triggers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.qrtz_triggers (sched_name, trigger_name, trigger_group, job_name, job_group, description, next_fire_time, prev_fire_time, priority, trigger_state, trigger_type, start_time, end_time, calendar_name, misfire_instr, job_data) FROM stdin;
\.


--
-- Data for Name: system_dept; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_dept (id, name, parent_id, sort, leader_user_id, phone, email, status, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
101	深圳总公司	100	1	104	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47	1	2023-12-02 09:53:35	0	1
102	长沙分公司	100	2	\N	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47		2021-12-15 05:01:40	0	1
103	研发部门	101	1	104	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47	1	2024-03-24 20:56:04	0	1
104	市场部门	101	2	\N	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47		2021-12-15 05:01:38	0	1
105	测试部门	101	3	\N	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47	1	2022-05-16 20:25:15	0	1
106	财务部门	101	4	103	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47	103	2022-01-15 21:32:22	0	1
107	运维部门	101	5	1	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47	1	2023-12-02 09:28:22	0	1
108	市场部门	102	1	\N	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47	1	2022-02-16 08:35:45	0	1
109	财务部门	102	2	\N	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47		2021-12-15 05:01:29	0	1
110	新部门	0	1	\N	\N	\N	0	110	2022-02-23 20:46:30	110	2022-02-23 20:46:30	0	121
111	顶级部门	0	1	\N	\N	\N	0	113	2022-03-07 21:44:50	113	2022-03-07 21:44:50	0	122
112	产品部门	101	100	1	\N	\N	1	1	2023-12-02 09:45:13	1	2023-12-02 09:45:31	0	1
113	支持部门	102	3	104	\N	\N	1	1	2023-12-02 09:47:38	1	2023-12-02 09:47:38	0	1
100	集团组织架构	0	0	1	15888888888	ry@qq.com	0	admin	2021-01-05 17:03:47	1	2024-07-11 16:30:01.379	0	1
\.


--
-- Data for Name: system_dict_data; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_dict_data (id, sort, label, value, dict_type, status, color_type, css_class, remark, creator, create_time, updater, update_time, deleted) FROM stdin;
1	1	男	1	system_user_sex	0	default	A	性别男	admin	2021-01-05 17:03:48	1	2022-03-29 00:14:39	0
2	2	女	2	system_user_sex	0	success		性别女	admin	2021-01-05 17:03:48	1	2023-11-15 23:30:37	0
8	1	正常	1	infra_job_status	0	success		正常状态	admin	2021-01-05 17:03:48	1	2022-02-16 19:33:38	0
9	2	暂停	2	infra_job_status	0	danger		停用状态	admin	2021-01-05 17:03:48	1	2022-02-16 19:33:45	0
12	1	系统内置	1	infra_config_type	0	danger		参数类型 - 系统内置	admin	2021-01-05 17:03:48	1	2022-02-16 19:06:02	0
13	2	自定义	2	infra_config_type	0	primary		参数类型 - 自定义	admin	2021-01-05 17:03:48	1	2022-02-16 19:06:07	0
14	1	通知	1	system_notice_type	0	success		通知	admin	2021-01-05 17:03:48	1	2022-02-16 13:05:57	0
15	2	公告	2	system_notice_type	0	info		公告	admin	2021-01-05 17:03:48	1	2022-02-16 13:06:01	0
16	0	其它	0	infra_operate_type	0	default		其它操作	admin	2021-01-05 17:03:48	1	2024-03-14 12:44:19	0
17	1	查询	1	infra_operate_type	0	info		查询操作	admin	2021-01-05 17:03:48	1	2024-03-14 12:44:20	0
18	2	新增	2	infra_operate_type	0	primary		新增操作	admin	2021-01-05 17:03:48	1	2024-03-14 12:44:21	0
19	3	修改	3	infra_operate_type	0	warning		修改操作	admin	2021-01-05 17:03:48	1	2024-03-14 12:44:22	0
20	4	删除	4	infra_operate_type	0	danger		删除操作	admin	2021-01-05 17:03:48	1	2024-03-14 12:44:23	0
22	5	导出	5	infra_operate_type	0	default		导出操作	admin	2021-01-05 17:03:48	1	2024-03-14 12:44:24	0
23	6	导入	6	infra_operate_type	0	default		导入操作	admin	2021-01-05 17:03:48	1	2024-03-14 12:44:25	0
27	1	开启	0	common_status	0	primary		开启状态	admin	2021-01-05 17:03:48	1	2022-02-16 08:00:39	0
28	2	关闭	1	common_status	0	info		关闭状态	admin	2021-01-05 17:03:48	1	2022-02-16 08:00:44	0
29	1	目录	1	system_menu_type	0			目录	admin	2021-01-05 17:03:48		2022-02-01 16:43:45	0
30	2	菜单	2	system_menu_type	0			菜单	admin	2021-01-05 17:03:48		2022-02-01 16:43:41	0
31	3	按钮	3	system_menu_type	0			按钮	admin	2021-01-05 17:03:48		2022-02-01 16:43:39	0
32	1	内置	1	system_role_type	0	danger		内置角色	admin	2021-01-05 17:03:48	1	2022-02-16 13:02:08	0
33	2	自定义	2	system_role_type	0	primary		自定义角色	admin	2021-01-05 17:03:48	1	2022-02-16 13:02:12	0
34	1	全部数据权限	1	system_data_scope	0			全部数据权限	admin	2021-01-05 17:03:48		2022-02-01 16:47:17	0
35	2	指定部门数据权限	2	system_data_scope	0			指定部门数据权限	admin	2021-01-05 17:03:48		2022-02-01 16:47:18	0
36	3	本部门数据权限	3	system_data_scope	0			本部门数据权限	admin	2021-01-05 17:03:48		2022-02-01 16:47:16	0
37	4	本部门及以下数据权限	4	system_data_scope	0			本部门及以下数据权限	admin	2021-01-05 17:03:48		2022-02-01 16:47:21	0
38	5	仅本人数据权限	5	system_data_scope	0			仅本人数据权限	admin	2021-01-05 17:03:48		2022-02-01 16:47:23	0
39	0	成功	0	system_login_result	0	success		登陆结果 - 成功		2021-01-18 06:17:36	1	2022-02-16 13:23:49	0
40	10	账号或密码不正确	10	system_login_result	0	primary		登陆结果 - 账号或密码不正确		2021-01-18 06:17:54	1	2022-02-16 13:24:27	0
41	20	用户被禁用	20	system_login_result	0	warning		登陆结果 - 用户被禁用		2021-01-18 06:17:54	1	2022-02-16 13:23:57	0
42	30	验证码不存在	30	system_login_result	0	info		登陆结果 - 验证码不存在		2021-01-18 06:17:54	1	2022-02-16 13:24:07	0
43	31	验证码不正确	31	system_login_result	0	info		登陆结果 - 验证码不正确		2021-01-18 06:17:54	1	2022-02-16 13:24:11	0
44	100	未知异常	100	system_login_result	0	danger		登陆结果 - 未知异常		2021-01-18 06:17:54	1	2022-02-16 13:24:23	0
45	1	是	true	infra_boolean_string	0	danger		Boolean 是否类型 - 是		2021-01-19 03:20:55	1	2022-03-15 23:01:45	0
46	1	否	false	infra_boolean_string	0	info		Boolean 是否类型 - 否		2021-01-19 03:20:55	1	2022-03-15 23:09:45	0
50	1	单表（增删改查）	1	infra_codegen_template_type	0			\N		2021-02-05 07:09:06		2022-03-10 16:33:15	0
51	2	树表（增删改查）	2	infra_codegen_template_type	0			\N		2021-02-05 07:14:46		2022-03-10 16:33:19	0
53	0	初始化中	0	infra_job_status	0	primary		\N		2021-02-07 07:46:49	1	2022-02-16 19:33:29	0
57	0	运行中	0	infra_job_log_status	0	primary		RUNNING		2021-02-08 10:04:24	1	2022-02-16 19:07:48	0
58	1	成功	1	infra_job_log_status	0	success		\N		2021-02-08 10:06:57	1	2022-02-16 19:07:52	0
59	2	失败	2	infra_job_log_status	0	warning		失败		2021-02-08 10:07:38	1	2022-02-16 19:07:56	0
60	1	会员	1	user_type	0	primary		\N		2021-02-26 00:16:27	1	2022-02-16 10:22:19	0
61	2	管理员	2	user_type	0	success		\N		2021-02-26 00:16:34	1	2022-02-16 10:22:22	0
62	0	未处理	0	infra_api_error_log_process_status	0	primary		\N		2021-02-26 07:07:19	1	2022-02-16 20:14:17	0
63	1	已处理	1	infra_api_error_log_process_status	0	success		\N		2021-02-26 07:07:26	1	2022-02-16 20:14:08	0
64	2	已忽略	2	infra_api_error_log_process_status	0	danger		\N		2021-02-26 07:07:34	1	2022-02-16 20:14:14	0
66	2	阿里云	ALIYUN	system_sms_channel_code	0	primary		\N	1	2021-04-05 01:05:26	1	2022-02-16 10:09:52	0
67	1	验证码	1	system_sms_template_type	0	warning		\N	1	2021-04-05 21:50:57	1	2022-02-16 12:48:30	0
68	2	通知	2	system_sms_template_type	0	primary		\N	1	2021-04-05 21:51:08	1	2022-02-16 12:48:27	0
69	0	营销	3	system_sms_template_type	0	danger		\N	1	2021-04-05 21:51:15	1	2022-02-16 12:48:22	0
70	0	初始化	0	system_sms_send_status	0	primary		\N	1	2021-04-11 20:18:33	1	2022-02-16 10:26:07	0
71	1	发送成功	10	system_sms_send_status	0	success		\N	1	2021-04-11 20:18:43	1	2022-02-16 10:25:56	0
72	2	发送失败	20	system_sms_send_status	0	danger		\N	1	2021-04-11 20:18:49	1	2022-02-16 10:26:03	0
73	3	不发送	30	system_sms_send_status	0	info		\N	1	2021-04-11 20:19:44	1	2022-02-16 10:26:10	0
74	0	等待结果	0	system_sms_receive_status	0	primary		\N	1	2021-04-11 20:27:43	1	2022-02-16 10:28:24	0
75	1	接收成功	10	system_sms_receive_status	0	success		\N	1	2021-04-11 20:29:25	1	2022-02-16 10:28:28	0
76	2	接收失败	20	system_sms_receive_status	0	danger		\N	1	2021-04-11 20:29:31	1	2022-02-16 10:28:32	0
77	0	调试(钉钉)	DEBUG_DING_TALK	system_sms_channel_code	0	info		\N	1	2021-04-13 00:20:37	1	2022-02-16 10:10:00	0
80	100	账号登录	100	system_login_type	0	primary		账号登录	1	2021-10-06 00:52:02	1	2022-02-16 13:11:34	0
81	101	社交登录	101	system_login_type	0	info		社交登录	1	2021-10-06 00:52:17	1	2022-02-16 13:11:40	0
83	200	主动登出	200	system_login_type	0	primary		主动登出	1	2021-10-06 00:52:58	1	2022-02-16 13:11:49	0
85	202	强制登出	202	system_login_type	0	danger		强制退出	1	2021-10-06 00:53:41	1	2022-02-16 13:11:57	0
86	0	病假	1	bpm_oa_leave_type	0	primary		\N	1	2021-09-21 22:35:28	1	2022-02-16 10:00:41	0
87	1	事假	2	bpm_oa_leave_type	0	info		\N	1	2021-09-21 22:36:11	1	2022-02-16 10:00:49	0
88	2	婚假	3	bpm_oa_leave_type	0	warning		\N	1	2021-09-21 22:36:38	1	2022-02-16 10:00:53	0
113	1	微信公众号支付	wx_pub	pay_channel_code	0	success		微信公众号支付	1	2021-12-03 10:40:24	1	2023-07-19 20:08:47	0
114	2	微信小程序支付	wx_lite	pay_channel_code	0	success		微信小程序支付	1	2021-12-03 10:41:06	1	2023-07-19 20:08:50	0
115	3	微信 App 支付	wx_app	pay_channel_code	0	success		微信 App 支付	1	2021-12-03 10:41:20	1	2023-07-19 20:08:56	0
116	10	支付宝 PC 网站支付	alipay_pc	pay_channel_code	0	primary		支付宝 PC 网站支付	1	2021-12-03 10:42:09	1	2023-07-19 20:09:12	0
117	11	支付宝 Wap 网站支付	alipay_wap	pay_channel_code	0	primary		支付宝 Wap 网站支付	1	2021-12-03 10:42:26	1	2023-07-19 20:09:16	0
118	12	支付宝 App 支付	alipay_app	pay_channel_code	0	primary		支付宝 App 支付	1	2021-12-03 10:42:55	1	2023-07-19 20:09:20	0
119	14	支付宝扫码支付	alipay_qr	pay_channel_code	0	primary		支付宝扫码支付	1	2021-12-03 10:43:10	1	2023-07-19 20:09:28	0
120	10	通知成功	10	pay_notify_status	0	success		通知成功	1	2021-12-03 11:02:41	1	2023-07-19 10:08:19	0
121	20	通知失败	20	pay_notify_status	0	danger		通知失败	1	2021-12-03 11:02:59	1	2023-07-19 10:08:21	0
122	0	等待通知	0	pay_notify_status	0	info		未通知	1	2021-12-03 11:03:10	1	2023-07-19 10:08:24	0
123	10	支付成功	10	pay_order_status	0	success		支付成功	1	2021-12-03 11:18:29	1	2023-07-19 18:04:28	0
124	30	支付关闭	30	pay_order_status	0	info		支付关闭	1	2021-12-03 11:18:42	1	2023-07-19 18:05:07	0
125	0	等待支付	0	pay_order_status	0	info		未支付	1	2021-12-03 11:18:18	1	2023-07-19 18:04:15	0
600	5	首页	1	promotion_banner_position	0	warning			1	2023-10-11 07:45:24	1	2023-10-11 07:45:38	0
601	4	秒杀活动页	2	promotion_banner_position	0	warning			1	2023-10-11 07:45:24	1	2023-10-11 07:45:38	0
602	3	砍价活动页	3	promotion_banner_position	0	warning			1	2023-10-11 07:45:24	1	2023-10-11 07:45:38	0
603	2	限时折扣页	4	promotion_banner_position	0	warning			1	2023-10-11 07:45:24	1	2023-10-11 07:45:38	0
604	1	满减送页	5	promotion_banner_position	0	warning			1	2023-10-11 07:45:24	1	2023-10-11 07:45:38	0
1118	0	等待退款	0	pay_refund_status	0	info		等待退款	1	2021-12-10 16:44:59	1	2023-07-19 10:14:39	0
1119	20	退款失败	20	pay_refund_status	0	danger		退款失败	1	2021-12-10 16:45:10	1	2023-07-19 10:15:10	0
1124	10	退款成功	10	pay_refund_status	0	success		退款成功	1	2021-12-10 16:46:26	1	2023-07-19 10:15:00	0
1127	1	审批中	1	bpm_process_instance_status	0	default		流程实例的状态 - 进行中	1	2022-01-07 23:47:22	1	2024-03-16 16:11:45	0
1128	2	审批通过	2	bpm_process_instance_status	0	success		流程实例的状态 - 已完成	1	2022-01-07 23:47:49	1	2024-03-16 16:11:54	0
1129	1	审批中	1	bpm_task_status	0	primary		流程实例的结果 - 处理中	1	2022-01-07 23:48:32	1	2024-03-08 22:41:37	0
1130	2	审批通过	2	bpm_task_status	0	success		流程实例的结果 - 通过	1	2022-01-07 23:48:45	1	2024-03-08 22:41:38	0
1131	3	审批不通过	3	bpm_task_status	0	danger		流程实例的结果 - 不通过	1	2022-01-07 23:48:55	1	2024-03-08 22:41:38	0
1132	4	已取消	4	bpm_task_status	0	info		流程实例的结果 - 撤销	1	2022-01-07 23:49:06	1	2024-03-08 22:41:39	0
1133	10	流程表单	10	bpm_model_form_type	0			流程的表单类型 - 流程表单	103	2022-01-11 23:51:30	103	2022-01-11 23:51:30	0
1134	20	业务表单	20	bpm_model_form_type	0			流程的表单类型 - 业务表单	103	2022-01-11 23:51:47	103	2022-01-11 23:51:47	0
1135	10	角色	10	bpm_task_candidate_strategy	0	info		任务分配规则的类型 - 角色	103	2022-01-12 23:21:22	1	2024-03-06 02:53:16	0
1136	20	部门的成员	20	bpm_task_candidate_strategy	0	primary		任务分配规则的类型 - 部门的成员	103	2022-01-12 23:21:47	1	2024-03-06 02:53:17	0
1137	21	部门的负责人	21	bpm_task_candidate_strategy	0	primary		任务分配规则的类型 - 部门的负责人	103	2022-01-12 23:33:36	1	2024-03-06 02:53:18	0
1138	30	用户	30	bpm_task_candidate_strategy	0	info		任务分配规则的类型 - 用户	103	2022-01-12 23:34:02	1	2024-03-06 02:53:19	0
1139	40	用户组	40	bpm_task_candidate_strategy	0	warning		任务分配规则的类型 - 用户组	103	2022-01-12 23:34:21	1	2024-03-06 02:53:20	0
1140	60	流程表达式	60	bpm_task_candidate_strategy	0	danger		任务分配规则的类型 - 流程表达式	103	2022-01-12 23:34:43	1	2024-03-06 02:53:20	0
1141	22	岗位	22	bpm_task_candidate_strategy	0	success		任务分配规则的类型 - 岗位	103	2022-01-14 18:41:55	1	2024-03-06 02:53:21	0
1145	1	管理后台	1	infra_codegen_scene	0			代码生成的场景枚举 - 管理后台	1	2022-02-02 13:15:06	1	2022-03-10 16:32:59	0
1146	2	用户 APP	2	infra_codegen_scene	0			代码生成的场景枚举 - 用户 APP	1	2022-02-02 13:15:19	1	2022-03-10 16:33:03	0
1150	1	数据库	1	infra_file_storage	0	default		\N	1	2022-03-15 00:25:28	1	2022-03-15 00:25:28	0
1151	10	本地磁盘	10	infra_file_storage	0	default		\N	1	2022-03-15 00:25:41	1	2022-03-15 00:25:56	0
1152	11	FTP 服务器	11	infra_file_storage	0	default		\N	1	2022-03-15 00:26:06	1	2022-03-15 00:26:10	0
1153	12	SFTP 服务器	12	infra_file_storage	0	default		\N	1	2022-03-15 00:26:22	1	2022-03-15 00:26:22	0
1154	20	S3 对象存储	20	infra_file_storage	0	default		\N	1	2022-03-15 00:26:31	1	2022-03-15 00:26:45	0
1155	103	短信登录	103	system_login_type	0	default		\N	1	2022-05-09 23:57:58	1	2022-05-09 23:58:09	0
1156	1	password	password	system_oauth2_grant_type	0	default		密码模式	1	2022-05-12 00:22:05	1	2022-05-11 16:26:01	0
1157	2	authorization_code	authorization_code	system_oauth2_grant_type	0	primary		授权码模式	1	2022-05-12 00:22:59	1	2022-05-11 16:26:02	0
1158	3	implicit	implicit	system_oauth2_grant_type	0	success		简化模式	1	2022-05-12 00:23:40	1	2022-05-11 16:26:05	0
1159	4	client_credentials	client_credentials	system_oauth2_grant_type	0	default		客户端模式	1	2022-05-12 00:23:51	1	2022-05-11 16:26:08	0
1160	5	refresh_token	refresh_token	system_oauth2_grant_type	0	info		刷新模式	1	2022-05-12 00:24:02	1	2022-05-11 16:26:11	0
1162	1	销售中	1	product_spu_status	0	success		商品 SPU 状态 - 销售中	1	2022-10-24 21:19:47	1	2022-10-24 21:20:38	0
1163	0	仓库中	0	product_spu_status	0	info		商品 SPU 状态 - 仓库中	1	2022-10-24 21:20:54	1	2022-10-24 21:21:22	0
1164	0	回收站	-1	product_spu_status	0	default		商品 SPU 状态 - 回收站	1	2022-10-24 21:21:11	1	2022-10-24 21:21:11	0
1165	1	满减	1	promotion_discount_type	0	success		优惠类型 - 满减	1	2022-11-01 12:46:41	1	2022-11-01 12:50:11	0
1166	2	折扣	2	promotion_discount_type	0	primary		优惠类型 - 折扣	1	2022-11-01 12:46:51	1	2022-11-01 12:50:08	0
1167	1	固定日期	1	promotion_coupon_template_validity_type	0	default		优惠劵模板的有限期类型 - 固定日期	1	2022-11-02 00:07:34	1	2022-11-04 00:07:49	0
1168	2	领取之后	2	promotion_coupon_template_validity_type	0	default		优惠劵模板的有限期类型 - 领取之后	1	2022-11-02 00:07:54	1	2022-11-04 00:07:52	0
1169	1	通用劵	1	promotion_product_scope	0	default		营销的商品范围 - 全部商品参与	1	2022-11-02 00:28:22	1	2023-09-28 00:27:42	0
1170	2	商品劵	2	promotion_product_scope	0	default		营销的商品范围 - 指定商品参与	1	2022-11-02 00:28:34	1	2023-09-28 00:27:44	0
1171	1	未使用	1	promotion_coupon_status	0	primary		优惠劵的状态 - 已领取	1	2022-11-04 00:15:08	1	2023-10-03 12:54:38	0
1172	2	已使用	2	promotion_coupon_status	0	success		优惠劵的状态 - 已使用	1	2022-11-04 00:15:21	1	2022-11-04 19:16:08	0
1173	3	已过期	3	promotion_coupon_status	0	info		优惠劵的状态 - 已过期	1	2022-11-04 00:15:43	1	2022-11-04 19:16:12	0
1174	1	直接领取	1	promotion_coupon_take_type	0	primary		优惠劵的领取方式 - 直接领取	1	2022-11-04 19:13:00	1	2022-11-04 19:13:25	0
1175	2	指定发放	2	promotion_coupon_take_type	0	success		优惠劵的领取方式 - 指定发放	1	2022-11-04 19:13:13	1	2022-11-04 19:14:48	0
1176	10	未开始	10	promotion_activity_status	0	primary		促销活动的状态枚举 - 未开始	1	2022-11-04 22:54:49	1	2022-11-04 22:55:53	0
1177	20	进行中	20	promotion_activity_status	0	success		促销活动的状态枚举 - 进行中	1	2022-11-04 22:55:06	1	2022-11-04 22:55:20	0
1178	30	已结束	30	promotion_activity_status	0	info		促销活动的状态枚举 - 已结束	1	2022-11-04 22:55:41	1	2022-11-04 22:55:41	0
1179	40	已关闭	40	promotion_activity_status	0	warning		促销活动的状态枚举 - 已关闭	1	2022-11-04 22:56:10	1	2022-11-04 22:56:18	0
1180	10	满 N 元	10	promotion_condition_type	0	primary		营销的条件类型 - 满 N 元	1	2022-11-04 22:59:45	1	2022-11-04 22:59:45	0
1181	20	满 N 件	20	promotion_condition_type	0	success		营销的条件类型 - 满 N 件	1	2022-11-04 23:00:02	1	2022-11-04 23:00:02	0
1182	10	申请售后	10	trade_after_sale_status	0	primary		交易售后状态 - 申请售后	1	2022-11-19 20:53:33	1	2022-11-19 20:54:42	0
1183	20	商品待退货	20	trade_after_sale_status	0	primary		交易售后状态 - 商品待退货	1	2022-11-19 20:54:36	1	2022-11-19 20:58:58	0
1184	30	商家待收货	30	trade_after_sale_status	0	primary		交易售后状态 - 商家待收货	1	2022-11-19 20:56:56	1	2022-11-19 20:59:20	0
1185	40	等待退款	40	trade_after_sale_status	0	primary		交易售后状态 - 等待退款	1	2022-11-19 20:59:54	1	2022-11-19 21:00:01	0
1186	50	退款成功	50	trade_after_sale_status	0	default		交易售后状态 - 退款成功	1	2022-11-19 21:00:33	1	2022-11-19 21:00:33	0
1187	61	买家取消	61	trade_after_sale_status	0	info		交易售后状态 - 买家取消	1	2022-11-19 21:01:29	1	2022-11-19 21:01:29	0
1188	62	商家拒绝	62	trade_after_sale_status	0	info		交易售后状态 - 商家拒绝	1	2022-11-19 21:02:17	1	2022-11-19 21:02:17	0
1189	63	商家拒收货	63	trade_after_sale_status	0	info		交易售后状态 - 商家拒收货	1	2022-11-19 21:02:37	1	2022-11-19 21:03:07	0
1190	10	售中退款	10	trade_after_sale_type	0	success		交易售后的类型 - 售中退款	1	2022-11-19 21:05:05	1	2022-11-19 21:38:23	0
1191	20	售后退款	20	trade_after_sale_type	0	primary		交易售后的类型 - 售后退款	1	2022-11-19 21:05:32	1	2022-11-19 21:38:32	0
1192	10	仅退款	10	trade_after_sale_way	0	primary		交易售后的方式 - 仅退款	1	2022-11-19 21:39:19	1	2022-11-19 21:39:19	0
1193	20	退货退款	20	trade_after_sale_way	0	success		交易售后的方式 - 退货退款	1	2022-11-19 21:39:38	1	2022-11-19 21:39:49	0
1194	10	微信小程序	10	terminal	0	default		终端 - 微信小程序	1	2022-12-10 10:51:11	1	2022-12-10 10:51:57	0
1195	20	H5 网页	20	terminal	0	default		终端 - H5 网页	1	2022-12-10 10:51:30	1	2022-12-10 10:51:59	0
1196	11	微信公众号	11	terminal	0	default		终端 - 微信公众号	1	2022-12-10 10:54:16	1	2022-12-10 10:52:01	0
1197	31	苹果 App	31	terminal	0	default		终端 - 苹果 App	1	2022-12-10 10:54:42	1	2022-12-10 10:52:18	0
1198	32	安卓 App	32	terminal	0	default		终端 - 安卓 App	1	2022-12-10 10:55:02	1	2022-12-10 10:59:17	0
1199	0	普通订单	0	trade_order_type	0	default		交易订单的类型 - 普通订单	1	2022-12-10 16:34:14	1	2022-12-10 16:34:14	0
1200	1	秒杀订单	1	trade_order_type	0	default		交易订单的类型 - 秒杀订单	1	2022-12-10 16:34:26	1	2022-12-10 16:34:26	0
1201	2	拼团订单	2	trade_order_type	0	default		交易订单的类型 - 拼团订单	1	2022-12-10 16:34:36	1	2022-12-10 16:34:36	0
1202	3	砍价订单	3	trade_order_type	0	default		交易订单的类型 - 砍价订单	1	2022-12-10 16:34:48	1	2022-12-10 16:34:48	0
1203	0	待支付	0	trade_order_status	0	default		交易订单状态 - 待支付	1	2022-12-10 16:49:29	1	2022-12-10 16:49:29	0
1204	10	待发货	10	trade_order_status	0	primary		交易订单状态 - 待发货	1	2022-12-10 16:49:53	1	2022-12-10 16:51:17	0
1205	20	已发货	20	trade_order_status	0	primary		交易订单状态 - 已发货	1	2022-12-10 16:50:13	1	2022-12-10 16:51:31	0
1206	30	已完成	30	trade_order_status	0	success		交易订单状态 - 已完成	1	2022-12-10 16:50:30	1	2022-12-10 16:51:06	0
1207	40	已取消	40	trade_order_status	0	danger		交易订单状态 - 已取消	1	2022-12-10 16:50:50	1	2022-12-10 16:51:00	0
1208	0	未售后	0	trade_order_item_after_sale_status	0	info		交易订单项的售后状态 - 未售后	1	2022-12-10 20:58:42	1	2022-12-10 20:59:29	0
1209	1	售后中	1	trade_order_item_after_sale_status	0	primary		交易订单项的售后状态 - 售后中	1	2022-12-10 20:59:21	1	2022-12-10 20:59:21	0
1210	2	已退款	2	trade_order_item_after_sale_status	0	success		交易订单项的售后状态 - 已退款	1	2022-12-10 20:59:46	1	2022-12-10 20:59:46	0
1211	1	完全匹配	1	mp_auto_reply_request_match	0	primary		公众号自动回复的请求关键字匹配模式 - 完全匹配	1	2023-01-16 23:30:39	1	2023-01-16 23:31:00	0
1513	7	审批通过中	7	bpm_task_status	0	success			1	2024-03-17 10:06:47	1	2024-03-08 22:41:41	0
1212	2	半匹配	2	mp_auto_reply_request_match	0	success		公众号自动回复的请求关键字匹配模式 - 半匹配	1	2023-01-16 23:30:55	1	2023-01-16 23:31:10	0
1213	1	文本	text	mp_message_type	0	default		公众号的消息类型 - 文本	1	2023-01-17 22:17:32	1	2023-01-17 22:17:39	0
1214	2	图片	image	mp_message_type	0	default		公众号的消息类型 - 图片	1	2023-01-17 22:17:32	1	2023-01-17 14:19:47	0
1215	3	语音	voice	mp_message_type	0	default		公众号的消息类型 - 语音	1	2023-01-17 22:17:32	1	2023-01-17 14:20:08	0
1216	4	视频	video	mp_message_type	0	default		公众号的消息类型 - 视频	1	2023-01-17 22:17:32	1	2023-01-17 14:21:08	0
1217	5	小视频	shortvideo	mp_message_type	0	default		公众号的消息类型 - 小视频	1	2023-01-17 22:17:32	1	2023-01-17 14:19:59	0
1218	6	图文	news	mp_message_type	0	default		公众号的消息类型 - 图文	1	2023-01-17 22:17:32	1	2023-01-17 14:22:54	0
1219	7	音乐	music	mp_message_type	0	default		公众号的消息类型 - 音乐	1	2023-01-17 22:17:32	1	2023-01-17 14:22:54	0
1220	8	地理位置	location	mp_message_type	0	default		公众号的消息类型 - 地理位置	1	2023-01-17 22:17:32	1	2023-01-17 14:23:51	0
1221	9	链接	link	mp_message_type	0	default		公众号的消息类型 - 链接	1	2023-01-17 22:17:32	1	2023-01-17 14:24:49	0
1222	10	事件	event	mp_message_type	0	default		公众号的消息类型 - 事件	1	2023-01-17 22:17:32	1	2023-01-17 14:24:49	0
1223	0	初始化	0	system_mail_send_status	0	primary		邮件发送状态 - 初始化\\n	1	2023-01-26 09:53:49	1	2023-01-26 16:36:14	0
1224	10	发送成功	10	system_mail_send_status	0	success		邮件发送状态 - 发送成功	1	2023-01-26 09:54:28	1	2023-01-26 16:36:22	0
1225	20	发送失败	20	system_mail_send_status	0	danger		邮件发送状态 - 发送失败	1	2023-01-26 09:54:50	1	2023-01-26 16:36:26	0
1226	30	不发送	30	system_mail_send_status	0	info		邮件发送状态 -  不发送	1	2023-01-26 09:55:06	1	2023-01-26 16:36:36	0
1227	1	通知公告	1	system_notify_template_type	0	primary		站内信模版的类型 - 通知公告	1	2023-01-28 10:35:59	1	2023-01-28 10:35:59	0
1228	2	系统消息	2	system_notify_template_type	0	success		站内信模版的类型 - 系统消息	1	2023-01-28 10:36:20	1	2023-01-28 10:36:25	0
1230	13	支付宝条码支付	alipay_bar	pay_channel_code	0	primary		支付宝条码支付	1	2023-02-18 23:32:24	1	2023-07-19 20:09:23	0
1231	10	Vue2 Element UI 标准模版	10	infra_codegen_front_type	0				1	2023-04-13 00:03:55	1	2023-04-13 00:03:55	0
1232	20	Vue3 Element Plus 标准模版	20	infra_codegen_front_type	0				1	2023-04-13 00:04:08	1	2023-04-13 00:04:08	0
1233	21	Vue3 Element Plus Schema 模版	21	infra_codegen_front_type	0				1	2023-04-13 00:04:26	1	2023-04-13 00:04:26	0
1234	30	Vue3 vben 模版	30	infra_codegen_front_type	0				1	2023-04-13 00:04:26	1	2023-04-13 00:04:26	0
1244	0	按件	1	trade_delivery_express_charge_mode	0				1	2023-05-21 22:46:40	1	2023-05-21 22:46:40	0
1245	1	按重量	2	trade_delivery_express_charge_mode	0				1	2023-05-21 22:46:58	1	2023-05-21 22:46:58	0
1246	2	按体积	3	trade_delivery_express_charge_mode	0				1	2023-05-21 22:47:18	1	2023-05-21 22:47:18	0
1335	11	订单积分抵扣	11	member_point_biz_type	0				1	2023-06-10 12:15:27	1	2023-10-11 07:41:43	0
1336	1	签到	1	member_point_biz_type	0				1	2023-06-10 12:15:48	1	2023-08-20 11:59:53	0
1341	20	已退款	20	pay_order_status	0	danger		已退款	1	2023-07-19 18:05:37	1	2023-07-19 18:05:37	0
1342	21	请求成功，但是结果失败	21	pay_notify_status	0	warning		请求成功，但是结果失败	1	2023-07-19 18:10:47	1	2023-07-19 18:11:38	0
1343	22	请求失败	22	pay_notify_status	0	warning		\N	1	2023-07-19 18:11:05	1	2023-07-19 18:11:27	0
1344	4	微信扫码支付	wx_native	pay_channel_code	0	success		微信扫码支付	1	2023-07-19 20:07:47	1	2023-07-19 20:09:03	0
1345	5	微信条码支付	wx_bar	pay_channel_code	0	success		微信条码支付\\n	1	2023-07-19 20:08:06	1	2023-07-19 20:09:08	0
1346	1	支付单	1	pay_notify_type	0	primary		支付单	1	2023-07-20 12:23:17	1	2023-07-20 12:23:17	0
1347	2	退款单	2	pay_notify_type	0	danger		\N	1	2023-07-20 12:23:26	1	2023-07-20 12:23:26	0
1348	20	模拟支付	mock	pay_channel_code	0	default		模拟支付	1	2023-07-29 11:10:51	1	2023-07-29 03:14:10	0
1349	12	订单积分抵扣（整单取消）	12	member_point_biz_type	0				1	2023-08-20 12:00:03	1	2023-10-11 07:42:01	0
1350	0	管理员调整	0	member_experience_biz_type	0			\N		2023-08-22 12:41:01		2023-08-22 12:41:01	0
1351	1	邀新奖励	1	member_experience_biz_type	0			\N		2023-08-22 12:41:01		2023-08-22 12:41:01	0
1352	11	下单奖励	11	member_experience_biz_type	0	success		\N		2023-08-22 12:41:01	1	2023-10-11 07:45:09	0
1353	12	下单奖励（整单取消）	12	member_experience_biz_type	0	warning		\N		2023-08-22 12:41:01	1	2023-10-11 07:45:01	0
1354	4	签到奖励	4	member_experience_biz_type	0			\N		2023-08-22 12:41:01		2023-08-22 12:41:01	0
1355	5	抽奖奖励	5	member_experience_biz_type	0			\N		2023-08-22 12:41:01		2023-08-22 12:41:01	0
1356	1	快递发货	1	trade_delivery_type	0				1	2023-08-23 00:04:55	1	2023-08-23 00:04:55	0
1357	2	用户自提	2	trade_delivery_type	0				1	2023-08-23 00:05:05	1	2023-08-23 00:05:05	0
1358	3	品类劵	3	promotion_product_scope	0	default			1	2023-09-01 23:43:07	1	2023-09-28 00:27:47	0
1359	1	人人分销	1	brokerage_enabled_condition	0			所有用户都可以分销		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1360	2	指定分销	2	brokerage_enabled_condition	0			仅可后台手动设置推广员		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1361	1	首次绑定	1	brokerage_bind_mode	0			只要用户没有推广人，随时都可以绑定推广关系		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1362	2	注册绑定	2	brokerage_bind_mode	0			仅新用户注册时才能绑定推广关系		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1363	3	覆盖绑定	3	brokerage_bind_mode	0			如果用户已经有推广人，推广人会被变更		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1364	1	钱包	1	brokerage_withdraw_type	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1365	2	银行卡	2	brokerage_withdraw_type	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1366	3	微信	3	brokerage_withdraw_type	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1367	4	支付宝	4	brokerage_withdraw_type	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1368	1	订单返佣	1	brokerage_record_biz_type	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1369	2	申请提现	2	brokerage_record_biz_type	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1370	3	申请提现驳回	3	brokerage_record_biz_type	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1371	0	待结算	0	brokerage_record_status	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1372	1	已结算	1	brokerage_record_status	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1373	2	已取消	2	brokerage_record_status	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1374	0	审核中	0	brokerage_withdraw_status	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1375	10	审核通过	10	brokerage_withdraw_status	0	success		\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1376	11	提现成功	11	brokerage_withdraw_status	0	success		\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1377	20	审核不通过	20	brokerage_withdraw_status	0	danger		\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1378	21	提现失败	21	brokerage_withdraw_status	0	danger		\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1379	0	工商银行	0	brokerage_bank_name	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1380	1	建设银行	1	brokerage_bank_name	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1381	2	农业银行	2	brokerage_bank_name	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1382	3	中国银行	3	brokerage_bank_name	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1383	4	交通银行	4	brokerage_bank_name	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1384	5	招商银行	5	brokerage_bank_name	0			\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0
1385	21	钱包	wallet	pay_channel_code	0	primary			1	2023-10-01 21:46:19	1	2023-10-01 21:48:01	0
1386	1	砍价中	1	promotion_bargain_record_status	0	default			1	2023-10-05 10:41:26	1	2023-10-05 10:41:26	0
1387	2	砍价成功	2	promotion_bargain_record_status	0	success			1	2023-10-05 10:41:39	1	2023-10-05 10:41:39	0
1388	3	砍价失败	3	promotion_bargain_record_status	0	warning			1	2023-10-05 10:41:57	1	2023-10-05 10:41:57	0
1389	1	拼团中	1	promotion_combination_record_status	0				1	2023-10-08 07:24:44	1	2023-10-08 07:24:44	0
1390	2	拼团成功	2	promotion_combination_record_status	0	success			1	2023-10-08 07:24:56	1	2023-10-08 07:24:56	0
1391	3	拼团失败	3	promotion_combination_record_status	0	warning			1	2023-10-08 07:25:11	1	2023-10-08 07:25:11	0
1392	2	管理员修改	2	member_point_biz_type	0	default			1	2023-10-11 07:41:34	1	2023-10-11 07:41:34	0
1393	13	订单积分抵扣（单个退款）	13	member_point_biz_type	0				1	2023-10-11 07:42:29	1	2023-10-11 07:42:29	0
1394	21	订单积分奖励	21	member_point_biz_type	0	default			1	2023-10-11 07:42:44	1	2023-10-11 07:42:44	0
1395	22	订单积分奖励（整单取消）	22	member_point_biz_type	0	default			1	2023-10-11 07:42:55	1	2023-10-11 07:43:01	0
1396	23	订单积分奖励（单个退款）	23	member_point_biz_type	0	default			1	2023-10-11 07:43:16	1	2023-10-11 07:43:16	0
1397	13	下单奖励（单个退款）	13	member_experience_biz_type	0	warning			1	2023-10-11 07:45:24	1	2023-10-11 07:45:38	0
1398	5	网上转账	5	crm_receivable_return_type	0	default			1	2023-10-18 21:55:24	1	2023-10-18 21:55:24	0
1399	6	支付宝	6	crm_receivable_return_type	0	default			1	2023-10-18 21:55:38	1	2023-10-18 21:55:38	0
1400	7	微信支付	7	crm_receivable_return_type	0	default			1	2023-10-18 21:55:53	1	2023-10-18 21:55:53	0
1401	8	其他	8	crm_receivable_return_type	0	default			1	2023-10-18 21:56:06	1	2023-10-18 21:56:06	0
1402	1	IT	1	crm_customer_industry	0	default			1	2023-10-28 23:02:15	1	2024-02-18 23:30:38	0
1403	2	金融业	2	crm_customer_industry	0	default			1	2023-10-28 23:02:29	1	2024-02-18 23:30:43	0
1404	3	房地产	3	crm_customer_industry	0	default			1	2023-10-28 23:02:41	1	2024-02-18 23:30:48	0
1405	4	商业服务	4	crm_customer_industry	0	default			1	2023-10-28 23:02:54	1	2024-02-18 23:30:54	0
1406	5	运输/物流	5	crm_customer_industry	0	default			1	2023-10-28 23:03:03	1	2024-02-18 23:31:00	0
1407	6	生产	6	crm_customer_industry	0	default			1	2023-10-28 23:03:13	1	2024-02-18 23:31:08	0
1408	7	政府	7	crm_customer_industry	0	default			1	2023-10-28 23:03:27	1	2024-02-18 23:31:13	0
1409	8	文化传媒	8	crm_customer_industry	0	default			1	2023-10-28 23:03:37	1	2024-02-18 23:31:20	0
1422	1	A （重点客户）	1	crm_customer_level	0	primary			1	2023-10-28 23:07:13	1	2023-10-28 23:07:13	0
1423	2	B （普通客户）	2	crm_customer_level	0	info			1	2023-10-28 23:07:35	1	2023-10-28 23:07:35	0
1424	3	C （非优先客户）	3	crm_customer_level	0	default			1	2023-10-28 23:07:53	1	2023-10-28 23:07:53	0
1425	1	促销	1	crm_customer_source	0	default			1	2023-10-28 23:08:29	1	2023-10-28 23:08:29	0
1426	2	搜索引擎	2	crm_customer_source	0	default			1	2023-10-28 23:08:39	1	2023-10-28 23:08:39	0
1427	3	广告	3	crm_customer_source	0	default			1	2023-10-28 23:08:47	1	2023-10-28 23:08:47	0
1428	4	转介绍	4	crm_customer_source	0	default			1	2023-10-28 23:08:58	1	2023-10-28 23:08:58	0
1429	5	线上注册	5	crm_customer_source	0	default			1	2023-10-28 23:09:12	1	2023-10-28 23:09:12	0
1430	6	线上咨询	6	crm_customer_source	0	default			1	2023-10-28 23:09:22	1	2023-10-28 23:09:22	0
1431	7	预约上门	7	crm_customer_source	0	default			1	2023-10-28 23:09:39	1	2023-10-28 23:09:39	0
1432	8	陌拜	8	crm_customer_source	0	default			1	2023-10-28 23:10:04	1	2023-10-28 23:10:04	0
1433	9	电话咨询	9	crm_customer_source	0	default			1	2023-10-28 23:10:18	1	2023-10-28 23:10:18	0
1434	10	邮件咨询	10	crm_customer_source	0	default			1	2023-10-28 23:10:33	1	2023-10-28 23:10:33	0
1435	10	Gitee	10	system_social_type	0				1	2023-11-04 13:04:42	1	2023-11-04 13:04:42	0
1436	20	钉钉	20	system_social_type	0				1	2023-11-04 13:04:54	1	2023-11-04 13:04:54	0
1437	30	企业微信	30	system_social_type	0				1	2023-11-04 13:05:09	1	2023-11-04 13:05:09	0
1438	31	微信公众平台	31	system_social_type	0				1	2023-11-04 13:05:18	1	2023-11-04 13:05:18	0
1439	32	微信开放平台	32	system_social_type	0				1	2023-11-04 13:05:30	1	2023-11-04 13:05:30	0
1440	34	微信小程序	34	system_social_type	0				1	2023-11-04 13:05:38	1	2023-11-04 13:07:16	0
1441	1	上架	1	crm_product_status	0	success			1	2023-10-30 21:49:34	1	2023-10-30 21:49:34	0
1442	0	下架	0	crm_product_status	0	success			1	2023-10-30 21:49:13	1	2023-10-30 21:49:13	0
1443	15	子表	15	infra_codegen_template_type	0	default			1	2023-11-13 23:06:16	1	2023-11-13 23:06:16	0
1444	10	主表（标准模式）	10	infra_codegen_template_type	0	default			1	2023-11-14 12:32:49	1	2023-11-14 12:32:49	0
1445	11	主表（ERP 模式）	11	infra_codegen_template_type	0	default			1	2023-11-14 12:33:05	1	2023-11-14 12:33:05	0
1446	12	主表（内嵌模式）	12	infra_codegen_template_type	0				1	2023-11-14 12:33:31	1	2023-11-14 12:33:31	0
1447	1	负责人	1	crm_permission_level	0	default			1	2023-11-30 09:53:12	1	2023-11-30 09:53:12	0
1448	2	只读	2	crm_permission_level	0				1	2023-11-30 09:53:29	1	2023-11-30 09:53:29	0
1449	3	读写	3	crm_permission_level	0				1	2023-11-30 09:53:36	1	2023-11-30 09:53:36	0
1450	0	未提交	0	crm_audit_status	0				1	2023-11-30 18:56:59	1	2023-11-30 18:56:59	0
1451	10	审批中	10	crm_audit_status	0				1	2023-11-30 18:57:10	1	2023-11-30 18:57:10	0
1452	20	审核通过	20	crm_audit_status	0				1	2023-11-30 18:57:24	1	2023-11-30 18:57:24	0
1453	30	审核不通过	30	crm_audit_status	0				1	2023-11-30 18:57:32	1	2023-11-30 18:57:32	0
1454	40	已取消	40	crm_audit_status	0				1	2023-11-30 18:57:42	1	2023-11-30 18:57:42	0
1456	1	支票	1	crm_receivable_return_type	0	default			1	2023-10-18 21:54:29	1	2023-10-18 21:54:29	0
1457	2	现金	2	crm_receivable_return_type	0	default			1	2023-10-18 21:54:41	1	2023-10-18 21:54:41	0
1458	3	邮政汇款	3	crm_receivable_return_type	0	default			1	2023-10-18 21:54:53	1	2023-10-18 21:54:53	0
1459	4	电汇	4	crm_receivable_return_type	0	default			1	2023-10-18 21:55:07	1	2023-10-18 21:55:07	0
1460	5	网上转账	5	crm_receivable_return_type	0	default			1	2023-10-18 21:55:24	1	2023-10-18 21:55:24	0
1461	1	个	1	crm_product_unit	0				1	2023-12-05 23:02:26	1	2023-12-05 23:02:26	0
1462	2	块	2	crm_product_unit	0				1	2023-12-05 23:02:34	1	2023-12-05 23:02:34	0
1463	3	只	3	crm_product_unit	0				1	2023-12-05 23:02:57	1	2023-12-05 23:02:57	0
1464	4	把	4	crm_product_unit	0				1	2023-12-05 23:03:05	1	2023-12-05 23:03:05	0
1465	5	枚	5	crm_product_unit	0				1	2023-12-05 23:03:14	1	2023-12-05 23:03:14	0
1466	6	瓶	6	crm_product_unit	0				1	2023-12-05 23:03:20	1	2023-12-05 23:03:20	0
1467	7	盒	7	crm_product_unit	0				1	2023-12-05 23:03:30	1	2023-12-05 23:03:30	0
1468	8	台	8	crm_product_unit	0				1	2023-12-05 23:03:41	1	2023-12-05 23:03:41	0
1469	9	吨	9	crm_product_unit	0				1	2023-12-05 23:03:48	1	2023-12-05 23:03:48	0
1470	10	千克	10	crm_product_unit	0				1	2023-12-05 23:04:03	1	2023-12-05 23:04:03	0
1471	11	米	11	crm_product_unit	0				1	2023-12-05 23:04:12	1	2023-12-05 23:04:12	0
1472	12	箱	12	crm_product_unit	0				1	2023-12-05 23:04:25	1	2023-12-05 23:04:25	0
1473	13	套	13	crm_product_unit	0				1	2023-12-05 23:04:34	1	2023-12-05 23:04:34	0
1474	1	打电话	1	crm_follow_up_type	0				1	2024-01-15 20:48:20	1	2024-01-15 20:48:20	0
1475	2	发短信	2	crm_follow_up_type	0				1	2024-01-15 20:48:31	1	2024-01-15 20:48:31	0
1476	3	上门拜访	3	crm_follow_up_type	0				1	2024-01-15 20:49:07	1	2024-01-15 20:49:07	0
1477	4	微信沟通	4	crm_follow_up_type	0				1	2024-01-15 20:49:15	1	2024-01-15 20:49:15	0
1478	4	钱包余额	4	pay_transfer_type	0	info			1	2023-10-28 16:28:37	1	2023-10-28 16:28:37	0
1479	3	银行卡	3	pay_transfer_type	0	default			1	2023-10-28 16:28:21	1	2023-10-28 16:28:21	0
1480	2	微信余额	2	pay_transfer_type	0	info			1	2023-10-28 16:28:07	1	2023-10-28 16:28:07	0
1481	1	支付宝余额	1	pay_transfer_type	0	default			1	2023-10-28 16:27:44	1	2023-10-28 16:27:44	0
1482	4	转账失败	30	pay_transfer_status	0	warning			1	2023-10-28 16:24:16	1	2023-10-28 16:24:16	0
1483	3	转账成功	20	pay_transfer_status	0	success			1	2023-10-28 16:23:50	1	2023-10-28 16:23:50	0
1484	2	转账进行中	10	pay_transfer_status	0	info			1	2023-10-28 16:23:12	1	2023-10-28 16:23:12	0
1485	1	等待转账	0	pay_transfer_status	0	default			1	2023-10-28 16:21:43	1	2023-10-28 16:23:22	0
1486	10	其它入库	10	erp_stock_record_biz_type	0				1	2024-02-05 18:07:25	1	2024-02-05 18:07:43	0
1487	11	其它入库（作废）	11	erp_stock_record_biz_type	0	danger			1	2024-02-05 18:08:07	1	2024-02-05 19:20:16	0
1488	20	其它出库	20	erp_stock_record_biz_type	0				1	2024-02-05 18:08:51	1	2024-02-05 18:08:51	0
1489	21	其它出库（作废）	21	erp_stock_record_biz_type	0	danger			1	2024-02-05 18:09:00	1	2024-02-05 19:20:10	0
1490	10	未审核	10	erp_audit_status	0	default			1	2024-02-06 00:00:21	1	2024-02-06 00:00:21	0
1491	20	已审核	20	erp_audit_status	0	success			1	2024-02-06 00:00:35	1	2024-02-06 00:00:35	0
1492	30	调拨入库	30	erp_stock_record_biz_type	0				1	2024-02-07 20:34:19	1	2024-02-07 12:36:31	0
1493	31	调拨入库（作废）	31	erp_stock_record_biz_type	0	danger			1	2024-02-07 20:34:29	1	2024-02-07 20:37:11	0
1494	32	调拨出库	32	erp_stock_record_biz_type	0				1	2024-02-07 20:34:38	1	2024-02-07 12:36:33	0
1495	33	调拨出库（作废）	33	erp_stock_record_biz_type	0	danger			1	2024-02-07 20:34:49	1	2024-02-07 20:37:06	0
1496	40	盘盈入库	40	erp_stock_record_biz_type	0				1	2024-02-08 08:53:00	1	2024-02-08 08:53:09	0
1497	41	盘盈入库（作废）	41	erp_stock_record_biz_type	0	danger			1	2024-02-08 08:53:39	1	2024-02-16 19:40:54	0
1498	42	盘亏出库	42	erp_stock_record_biz_type	0				1	2024-02-08 08:54:16	1	2024-02-08 08:54:16	0
1499	43	盘亏出库（作废）	43	erp_stock_record_biz_type	0	danger			1	2024-02-08 08:54:31	1	2024-02-16 19:40:46	0
1500	50	销售出库	50	erp_stock_record_biz_type	0				1	2024-02-11 21:47:25	1	2024-02-11 21:50:40	0
1501	51	销售出库（作废）	51	erp_stock_record_biz_type	0	danger			1	2024-02-11 21:47:37	1	2024-02-11 21:51:12	0
1502	60	销售退货入库	60	erp_stock_record_biz_type	0				1	2024-02-12 06:51:05	1	2024-02-12 06:51:05	0
1503	61	销售退货入库（作废）	61	erp_stock_record_biz_type	0	danger			1	2024-02-12 06:51:18	1	2024-02-12 06:51:18	0
1504	70	采购入库	70	erp_stock_record_biz_type	0				1	2024-02-16 13:10:02	1	2024-02-16 13:10:02	0
1505	71	采购入库（作废）	71	erp_stock_record_biz_type	0	danger			1	2024-02-16 13:10:10	1	2024-02-16 19:40:40	0
1506	80	采购退货出库	80	erp_stock_record_biz_type	0				1	2024-02-16 13:10:17	1	2024-02-16 13:10:17	0
1507	81	采购退货出库（作废）	81	erp_stock_record_biz_type	0	danger			1	2024-02-16 13:10:26	1	2024-02-16 19:40:33	0
1509	3	审批不通过	3	bpm_process_instance_status	0	danger			1	2024-03-16 16:12:06	1	2024-03-16 16:12:06	0
1510	4	已取消	4	bpm_process_instance_status	0	warning			1	2024-03-16 16:12:22	1	2024-03-16 16:12:22	0
1511	5	已退回	5	bpm_task_status	0	warning			1	2024-03-16 19:10:46	1	2024-03-08 22:41:40	0
1512	6	委派中	6	bpm_task_status	0	primary			1	2024-03-17 10:06:22	1	2024-03-08 22:41:40	0
1514	0	待审批	0	bpm_task_status	0	info			1	2024-03-17 10:07:11	1	2024-03-08 22:41:42	0
1515	35	发起人自选	35	bpm_task_candidate_strategy	0				1	2024-03-22 19:45:16	1	2024-03-22 19:45:16	0
1516	1	执行监听器	execution	bpm_process_listener_type	0	primary			1	2024-03-23 12:54:03	1	2024-03-23 19:14:19	0
1517	1	任务监听器	task	bpm_process_listener_type	0	success			1	2024-03-23 12:54:13	1	2024-03-23 19:14:24	0
1526	1	Java 类	class	bpm_process_listener_value_type	0	primary			1	2024-03-23 15:08:45	1	2024-03-23 19:14:32	0
1527	2	表达式	expression	bpm_process_listener_value_type	0	success			1	2024-03-23 15:09:06	1	2024-03-23 19:14:38	0
1528	3	代理表达式	delegateExpression	bpm_process_listener_value_type	0	info			1	2024-03-23 15:11:23	1	2024-03-23 19:14:41	0
1529	1	天	1	date_interval	0				1	2024-03-29 22:50:26	1	2024-03-29 22:50:26	0
1530	2	周	2	date_interval	0				1	2024-03-29 22:50:36	1	2024-03-29 22:50:36	0
1531	3	月	3	date_interval	0				1	2024-03-29 22:50:46	1	2024-03-29 22:50:54	0
1532	4	季度	4	date_interval	0				1	2024-03-29 22:51:01	1	2024-03-29 22:51:01	0
1533	5	年	5	date_interval	0				1	2024-03-29 22:51:07	1	2024-03-29 22:51:07	0
1534	1	赢单	1	crm_business_end_status_type	0	success			1	2024-04-13 23:26:57	1	2024-04-13 23:26:57	0
1535	2	输单	2	crm_business_end_status_type	0	primary			1	2024-04-13 23:27:31	1	2024-04-13 23:27:31	0
1536	3	无效	3	crm_business_end_status_type	0	info			1	2024-04-13 23:27:59	1	2024-04-13 23:27:59	0
\.


--
-- Data for Name: system_dict_type; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_dict_type (id, name, type, status, remark, creator, create_time, updater, update_time, deleted, deleted_time) FROM stdin;
1	用户性别	system_user_sex	0	\N	admin	2021-01-05 17:03:48	1	2022-05-16 20:29:32	0	\N
6	参数类型	infra_config_type	0	\N	admin	2021-01-05 17:03:48		2022-02-01 16:36:54	0	\N
7	通知类型	system_notice_type	0	\N	admin	2021-01-05 17:03:48		2022-02-01 16:35:26	0	\N
9	操作类型	infra_operate_type	0	\N	admin	2021-01-05 17:03:48	1	2024-03-14 12:44:01	0	\N
10	系统状态	common_status	0	\N	admin	2021-01-05 17:03:48		2022-02-01 16:21:28	0	\N
11	Boolean 是否类型	infra_boolean_string	0	boolean 转是否		2021-01-19 03:20:08		2022-02-01 16:37:10	0	\N
104	登陆结果	system_login_result	0	登陆结果		2021-01-18 06:17:11		2022-02-01 16:36:00	0	\N
106	代码生成模板类型	infra_codegen_template_type	0	\N		2021-02-05 07:08:06	1	2022-05-16 20:26:50	0	\N
107	定时任务状态	infra_job_status	0	\N		2021-02-07 07:44:16		2022-02-01 16:51:11	0	\N
108	定时任务日志状态	infra_job_log_status	0	\N		2021-02-08 10:03:51		2022-02-01 16:50:43	0	\N
109	用户类型	user_type	0	\N		2021-02-26 00:15:51		2021-02-26 00:15:51	0	\N
110	API 异常数据的处理状态	infra_api_error_log_process_status	0	\N		2021-02-26 07:07:01		2022-02-01 16:50:53	0	\N
111	短信渠道编码	system_sms_channel_code	0	\N	1	2021-04-05 01:04:50	1	2022-02-16 02:09:08	0	\N
112	短信模板的类型	system_sms_template_type	0	\N	1	2021-04-05 21:50:43	1	2022-02-01 16:35:06	0	\N
113	短信发送状态	system_sms_send_status	0	\N	1	2021-04-11 20:18:03	1	2022-02-01 16:35:09	0	\N
114	短信接收状态	system_sms_receive_status	0	\N	1	2021-04-11 20:27:14	1	2022-02-01 16:35:14	0	\N
116	登陆日志的类型	system_login_type	0	登陆日志的类型	1	2021-10-06 00:50:46	1	2022-02-01 16:35:56	0	\N
117	OA 请假类型	bpm_oa_leave_type	0	\N	1	2021-09-21 22:34:33	1	2022-01-22 10:41:37	0	\N
130	支付渠道编码类型	pay_channel_code	0	支付渠道的编码	1	2021-12-03 10:35:08	1	2023-07-10 10:11:39	0	\N
131	支付回调状态	pay_notify_status	0	支付回调状态（包括退款回调）	1	2021-12-03 10:53:29	1	2023-07-19 18:09:43	0	\N
132	支付订单状态	pay_order_status	0	支付订单状态	1	2021-12-03 11:17:50	1	2021-12-03 11:17:50	0	\N
134	退款订单状态	pay_refund_status	0	退款订单状态	1	2021-12-10 16:42:50	1	2023-07-19 10:13:17	0	\N
139	流程实例的状态	bpm_process_instance_status	0	流程实例的状态	1	2022-01-07 23:46:42	1	2022-01-07 23:46:42	0	\N
140	流程实例的结果	bpm_task_status	0	流程实例的结果	1	2022-01-07 23:48:10	1	2024-03-08 22:42:03	0	\N
141	流程的表单类型	bpm_model_form_type	0	流程的表单类型	103	2022-01-11 23:50:45	103	2022-01-11 23:50:45	0	\N
142	任务分配规则的类型	bpm_task_candidate_strategy	0	BPM 任务的候选人的策略	103	2022-01-12 23:21:04	103	2024-03-06 02:53:59	0	\N
144	代码生成的场景枚举	infra_codegen_scene	0	代码生成的场景枚举	1	2022-02-02 13:14:45	1	2022-03-10 16:33:46	0	\N
145	角色类型	system_role_type	0	角色类型	1	2022-02-16 13:01:46	1	2022-02-16 13:01:46	0	\N
146	文件存储器	infra_file_storage	0	文件存储器	1	2022-03-15 00:24:38	1	2022-03-15 00:24:38	0	\N
147	OAuth 2.0 授权类型	system_oauth2_grant_type	0	OAuth 2.0 授权类型（模式）	1	2022-05-12 00:20:52	1	2022-05-11 16:25:49	0	\N
149	商品 SPU 状态	product_spu_status	0	商品 SPU 状态	1	2022-10-24 21:19:04	1	2022-10-24 21:19:08	0	\N
150	优惠类型	promotion_discount_type	0	优惠类型	1	2022-11-01 12:46:06	1	2022-11-01 12:46:06	0	\N
151	优惠劵模板的有限期类型	promotion_coupon_template_validity_type	0	优惠劵模板的有限期类型	1	2022-11-02 00:06:20	1	2022-11-04 00:08:26	0	\N
152	营销的商品范围	promotion_product_scope	0	营销的商品范围	1	2022-11-02 00:28:01	1	2022-11-02 00:28:01	0	\N
153	优惠劵的状态	promotion_coupon_status	0	优惠劵的状态	1	2022-11-04 00:14:49	1	2022-11-04 00:14:49	0	\N
154	优惠劵的领取方式	promotion_coupon_take_type	0	优惠劵的领取方式	1	2022-11-04 19:12:27	1	2022-11-04 19:12:27	0	\N
155	促销活动的状态	promotion_activity_status	0	促销活动的状态	1	2022-11-04 22:54:23	1	2022-11-04 22:54:23	0	\N
156	营销的条件类型	promotion_condition_type	0	营销的条件类型	1	2022-11-04 22:59:23	1	2022-11-04 22:59:23	0	\N
157	交易售后状态	trade_after_sale_status	0	交易售后状态	1	2022-11-19 20:52:56	1	2022-11-19 20:52:56	0	\N
158	交易售后的类型	trade_after_sale_type	0	交易售后的类型	1	2022-11-19 21:04:09	1	2022-11-19 21:04:09	0	\N
159	交易售后的方式	trade_after_sale_way	0	交易售后的方式	1	2022-11-19 21:39:04	1	2022-11-19 21:39:04	0	\N
160	终端	terminal	0	终端	1	2022-12-10 10:50:50	1	2022-12-10 10:53:11	0	\N
161	交易订单的类型	trade_order_type	0	交易订单的类型	1	2022-12-10 16:33:54	1	2022-12-10 16:33:54	0	\N
162	交易订单的状态	trade_order_status	0	交易订单的状态	1	2022-12-10 16:48:44	1	2022-12-10 16:48:44	0	\N
163	交易订单项的售后状态	trade_order_item_after_sale_status	0	交易订单项的售后状态	1	2022-12-10 20:58:08	1	2022-12-10 20:58:08	0	\N
164	公众号自动回复的请求关键字匹配模式	mp_auto_reply_request_match	0	公众号自动回复的请求关键字匹配模式	1	2023-01-16 23:29:56	1	2023-01-16 23:29:56	0	1970-01-01 00:00:00
165	公众号的消息类型	mp_message_type	0	公众号的消息类型	1	2023-01-17 22:17:09	1	2023-01-17 22:17:09	0	1970-01-01 00:00:00
166	邮件发送状态	system_mail_send_status	0	邮件发送状态	1	2023-01-26 09:53:13	1	2023-01-26 09:53:13	0	1970-01-01 00:00:00
167	站内信模版的类型	system_notify_template_type	0	站内信模版的类型	1	2023-01-28 10:35:10	1	2023-01-28 10:35:10	0	1970-01-01 00:00:00
168	代码生成的前端类型	infra_codegen_front_type	0		1	2023-04-12 23:57:52	1	2023-04-12 23:57:52	0	1970-01-01 00:00:00
170	快递计费方式	trade_delivery_express_charge_mode	0	用于商城交易模块配送管理	1	2023-05-21 22:45:03	1	2023-05-21 22:45:03	0	1970-01-01 00:00:00
171	积分业务类型	member_point_biz_type	0		1	2023-06-10 12:15:00	1	2023-06-28 13:48:20	0	1970-01-01 00:00:00
173	支付通知类型	pay_notify_type	0	\N	1	2023-07-20 12:23:03	1	2023-07-20 12:23:03	0	1970-01-01 00:00:00
174	会员经验业务类型	member_experience_biz_type	0	\N		2023-08-22 12:41:01		2023-08-22 12:41:01	0	\N
175	交易配送类型	trade_delivery_type	0		1	2023-08-23 00:03:14	1	2023-08-23 00:03:14	0	1970-01-01 00:00:00
176	分佣模式	brokerage_enabled_condition	0	\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0	\N
177	分销关系绑定模式	brokerage_bind_mode	0	\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0	\N
178	佣金提现类型	brokerage_withdraw_type	0	\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0	\N
179	佣金记录业务类型	brokerage_record_biz_type	0	\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0	\N
180	佣金记录状态	brokerage_record_status	0	\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0	\N
181	佣金提现状态	brokerage_withdraw_status	0	\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0	\N
182	佣金提现银行	brokerage_bank_name	0	\N		2023-09-28 02:46:05		2023-09-28 02:46:05	0	\N
183	砍价记录的状态	promotion_bargain_record_status	0		1	2023-10-05 10:41:08	1	2023-10-05 10:41:08	0	1970-01-01 00:00:00
184	拼团记录的状态	promotion_combination_record_status	0		1	2023-10-08 07:24:25	1	2023-10-08 07:24:25	0	1970-01-01 00:00:00
185	回款-回款方式	crm_receivable_return_type	0	回款-回款方式	1	2023-10-18 21:54:10	1	2023-10-18 21:54:10	0	1970-01-01 00:00:00
186	CRM 客户行业	crm_customer_industry	0	CRM 客户所属行业	1	2023-10-28 22:57:07	1	2024-02-18 23:30:22	0	\N
187	客户等级	crm_customer_level	0	CRM 客户等级	1	2023-10-28 22:59:12	1	2023-10-28 15:11:16	0	\N
188	客户来源	crm_customer_source	0	CRM 客户来源	1	2023-10-28 23:00:34	1	2023-10-28 15:11:16	0	\N
600	Banner 位置	promotion_banner_position	0		1	2023-10-08 07:24:25	1	2023-11-04 13:04:02	0	1970-01-01 00:00:00
601	社交类型	system_social_type	0		1	2023-11-04 13:03:54	1	2023-11-04 13:03:54	0	1970-01-01 00:00:00
604	产品状态	crm_product_status	0		1	2023-10-30 21:47:59	1	2023-10-30 21:48:45	0	1970-01-01 00:00:00
605	CRM 数据权限的级别	crm_permission_level	0		1	2023-11-30 09:51:59	1	2023-11-30 09:51:59	0	1970-01-01 00:00:00
606	CRM 审批状态	crm_audit_status	0		1	2023-11-30 18:56:23	1	2023-11-30 18:56:23	0	1970-01-01 00:00:00
607	CRM 产品单位	crm_product_unit	0		1	2023-12-05 23:01:51	1	2023-12-05 23:01:51	0	1970-01-01 00:00:00
608	CRM 跟进方式	crm_follow_up_type	0		1	2024-01-15 20:48:05	1	2024-01-15 20:48:05	0	1970-01-01 00:00:00
609	支付转账类型	pay_transfer_type	0		1	2023-10-28 16:27:18	1	2023-10-28 16:27:18	0	1970-01-01 00:00:00
610	转账订单状态	pay_transfer_status	0		1	2023-10-28 16:18:32	1	2023-10-28 16:18:32	0	1970-01-01 00:00:00
611	ERP 库存明细的业务类型	erp_stock_record_biz_type	0	ERP 库存明细的业务类型	1	2024-02-05 18:07:02	1	2024-02-05 18:07:02	0	1970-01-01 00:00:00
612	ERP 审批状态	erp_audit_status	0		1	2024-02-06 00:00:07	1	2024-02-06 00:00:07	0	1970-01-01 00:00:00
613	BPM 监听器类型	bpm_process_listener_type	0		1	2024-03-23 12:52:24	1	2024-03-09 15:54:28	0	1970-01-01 00:00:00
615	BPM 监听器值类型	bpm_process_listener_value_type	0		1	2024-03-23 13:00:31	1	2024-03-23 13:00:31	0	1970-01-01 00:00:00
616	时间间隔	date_interval	0		1	2024-03-29 22:50:09	1	2024-03-29 22:50:09	0	1970-01-01 00:00:00
619	CRM 商机结束状态类型	crm_business_end_status_type	0		1	2024-04-13 23:23:00	1	2024-04-13 23:23:00	0	1970-01-01 00:00:00
\.


--
-- Data for Name: system_login_log; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_login_log (id, log_type, trace_id, user_id, user_type, username, result, user_ip, user_agent, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: system_mail_account; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_mail_account (id, mail, username, password, host, port, ssl_enable, starttls_enable, creator, create_time, updater, update_time, deleted) FROM stdin;
1	7684413@qq.com	7684413@qq.com	1234576	127.0.0.1	8080	f	f	1	2023-01-25 17:39:52	1	2024-04-24 09:13:56	0
2	ydym_test@163.com	ydym_test@163.com	WBZTEINMIFVRYSOE	smtp.163.com	465	t	f	1	2023-01-26 01:26:03	1	2023-04-12 22:39:38	0
3	76854114@qq.com	3335	11234	yunai1.cn	466	f	f	1	2023-01-27 15:06:38	1	2023-01-27 07:08:36	1
4	7685413x@qq.com	2	3	4	5	t	f	1	2023-04-12 23:05:06	1	2023-04-12 15:05:11	1
\.


--
-- Data for Name: system_mail_log; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_mail_log (id, user_id, user_type, to_mail, account_id, from_mail, template_id, template_code, template_nickname, template_title, template_content, template_params, send_status, send_time, send_message_id, send_exception, creator, create_time, updater, update_time, deleted) FROM stdin;
\.


--
-- Data for Name: system_mail_template; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_mail_template (id, name, code, account_id, nickname, title, content, params, status, remark, creator, create_time, updater, update_time, deleted) FROM stdin;
13	后台用户短信登录	admin-sms-login	1	奥特曼	你猜我猜	<p>您的验证码是{code}，名字是{name}</p>	["code","name"]	0	3	1	2021-10-11 08:10:00	1	2023-12-02 19:51:14	0
14	测试模版	test_01	2	芋艿	一个标题	<p>你是 {key01} 吗？</p><p><br></p><p>是的话，赶紧 {key02} 一下！</p>	["key01","key02"]	0	\N	1	2023-01-26 01:27:40	1	2023-01-27 10:32:16	0
15	3	2	2	7	4	<p>45</p>	[]	1	80	1	2023-01-27 15:50:35	1	2023-01-27 16:34:49	0
\.


--
-- Data for Name: system_menu; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) FROM stdin;
100	用户管理	system:user:list	2	1	1	user	ep:avatar	system/user/index	SystemUser	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-02-29 01:02:04	0
101	角色管理		2	2	1	role	ep:user	system/role/index	SystemRole	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-02-29 01:03:28	0
102	菜单管理		2	3	1	menu	ep:menu	system/menu/index	SystemMenu	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-02-29 01:03:50	0
103	部门管理		2	4	1	dept	fa:address-card	system/dept/index	SystemDept	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-02-29 01:06:28	0
104	岗位管理		2	5	1	post	fa:address-book-o	system/post/index	SystemPost	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-02-29 01:06:39	0
105	字典管理		2	6	1	dict	ep:collection	system/dict/index	SystemDictType	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-02-29 01:07:12	0
109	令牌管理		2	2	1261	token	fa:key	system/oauth2/token/index	SystemTokenClient	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-02-29 01:13:48	0
111	MySQL 监控		2	1	2740	druid	fa-solid:box	infra/druid/index	InfraDruid	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-04-23 00:05:58	0
112	Java 监控		2	3	2740	admin-server	ep:coffee-cup	infra/server/index	InfraAdminServer	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-04-23 00:06:57	0
113	Redis 监控		2	2	2740	redis	fa:reddit-square	infra/redis/index	InfraRedis	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-04-23 00:06:09	0
500	操作日志		2	1	108	operate-log	ep:position	system/operatelog/index	SystemOperateLog	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-02-29 01:09:59	0
501	登录日志		2	2	108	login-log	ep:promotion	system/loginlog/index	SystemLoginLog	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-02-29 01:10:29	0
1001	用户查询	system:user:query	3	1	100		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1002	用户新增	system:user:create	3	2	100				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1003	用户修改	system:user:update	3	3	100				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1004	用户删除	system:user:delete	3	4	100				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1005	用户导出	system:user:export	3	5	100		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1006	用户导入	system:user:import	3	6	100		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1007	重置密码	system:user:update-password	3	7	100				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1008	角色查询	system:role:query	3	1	101		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1009	角色新增	system:role:create	3	2	101				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1010	角色修改	system:role:update	3	3	101				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1011	角色删除	system:role:delete	3	4	101				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1012	角色导出	system:role:export	3	5	101		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1013	菜单查询	system:menu:query	3	1	102		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1014	菜单新增	system:menu:create	3	2	102		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1015	菜单修改	system:menu:update	3	3	102		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1016	菜单删除	system:menu:delete	3	4	102		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1017	部门查询	system:dept:query	3	1	103		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1018	部门新增	system:dept:create	3	2	103				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1019	部门修改	system:dept:update	3	3	103				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1020	部门删除	system:dept:delete	3	4	103				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1021	岗位查询	system:post:query	3	1	104		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1022	岗位新增	system:post:create	3	2	104				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1023	岗位修改	system:post:update	3	3	104				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1024	岗位删除	system:post:delete	3	4	104				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1025	岗位导出	system:post:export	3	5	104		#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1026	字典查询	system:dict:query	3	1	105	#	#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1027	字典新增	system:dict:create	3	2	105				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1028	字典修改	system:dict:update	3	3	105				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1029	字典删除	system:dict:delete	3	4	105				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1030	字典导出	system:dict:export	3	5	105	#	#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1031	配置查询	infra:config:query	3	1	106				\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1032	配置新增	infra:config:create	3	2	106				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1	系统管理		1	40	0	/system	ep:tools	\N	\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-08-02 11:47:05.494	0
116	文档管理	infra:swagger:list	2	99	2759	swagger	fa:fighter-jet	infra/swagger/index	InfraSwagger	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-09 11:20:34.311	0
110	定时任务		2	25	2759	job	fa-solid:tasks	infra/job/index	InfraJob	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-09 11:20:15.039	0
107	通知公告		2	4	2739	notice	ep:takeaway-box	system/notice/index	SystemNotice	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-04-22 23:56:17	1
2	基础设施		1	20	0	/infra	ep:monitor	\N	\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-03-01 08:28:40	1
114	表单构建	infra:build:list	2	2	115	build	fa:wpforms	infra/build/index	InfraBuild	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-09 11:35:42.67	1
106	配置管理		2	30	2759	config	fa:connectdevelop	infra/config/index	InfraConfig	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-09 11:20:23.576	0
108	日志管理		1	35	1	/log	ep:document-copy		\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-24 10:59:05.715	0
1033	配置修改	infra:config:update	3	3	106				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1034	配置删除	infra:config:delete	3	4	106				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	0
1035	配置导出	infra:config:export	3	5	106				\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1040	操作查询	system:operate-log:query	3	1	500				\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1042	日志导出	system:operate-log:export	3	2	500				\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1043	登录查询	system:login-log:query	3	1	501	#	#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1045	日志导出	system:login-log:export	3	3	501	#	#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1046	令牌列表	system:oauth2-token:page	3	1	109				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-05-09 23:54:42	0
1048	令牌删除	system:oauth2-token:delete	3	2	109				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-05-09 23:54:53	0
1050	任务新增	infra:job:create	3	2	110				\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1051	任务修改	infra:job:update	3	3	110				\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1052	任务删除	infra:job:delete	3	4	110				\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1053	状态修改	infra:job:update	3	5	110				\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1054	任务导出	infra:job:export	3	7	110				\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	0
1063	设置角色菜单权限	system:permission:assign-role-menu	3	6	101				\N	0	t	t	t		2021-01-06 17:53:44		2022-04-20 17:03:10	0
1064	设置角色数据权限	system:permission:assign-role-data-scope	3	7	101				\N	0	t	t	t		2021-01-06 17:56:31		2022-04-20 17:03:10	0
1065	设置用户角色	system:permission:assign-user-role	3	8	101				\N	0	t	t	t		2021-01-07 10:23:28		2022-04-20 17:03:10	0
1066	获得 Redis 监控信息	infra:redis:get-monitor-info	3	1	113				\N	0	t	t	t		2021-01-26 01:02:31		2022-04-20 17:03:10	0
1067	获得 Redis Key 列表	infra:redis:get-key-list	3	2	113				\N	0	t	t	t		2021-01-26 01:02:52		2022-04-20 17:03:10	0
1075	任务触发	infra:job:trigger	3	8	110				\N	0	t	t	t		2021-02-07 13:03:10		2022-04-20 17:03:10	0
1077	链路追踪		2	4	2740	skywalking	fa:eye	infra/skywalking/index	InfraSkyWalking	0	t	t	t		2021-02-08 20:41:31	1	2024-04-23 00:07:15	0
1078	访问日志		2	1	1083	api-access-log	ep:place	infra/apiAccessLog/index	InfraApiAccessLog	0	t	t	t		2021-02-26 01:32:59	1	2024-02-29 08:54:57	0
1082	日志导出	infra:api-access-log:export	3	2	1078				\N	0	t	t	t		2021-02-26 01:32:59	1	2022-04-20 17:03:10	0
1084	错误日志	infra:api-error-log:query	2	2	1083	api-error-log	ep:warning-filled	infra/apiErrorLog/index	InfraApiErrorLog	0	t	t	t		2021-02-26 07:53:20	1	2024-02-29 08:55:17	0
1085	日志处理	infra:api-error-log:update-status	3	2	1084				\N	0	t	t	t		2021-02-26 07:53:20	1	2022-04-20 17:03:10	0
1086	日志导出	infra:api-error-log:export	3	3	1084				\N	0	t	t	t		2021-02-26 07:53:20	1	2022-04-20 17:03:10	0
1087	任务查询	infra:job:query	3	1	110				\N	0	t	t	t	1	2021-03-10 01:26:19	1	2022-04-20 17:03:10	0
1088	日志查询	infra:api-access-log:query	3	1	1078				\N	0	t	t	t	1	2021-03-10 01:28:04	1	2022-04-20 17:03:10	0
1089	日志查询	infra:api-error-log:query	3	1	1084				\N	0	t	t	t	1	2021-03-10 01:29:09	1	2022-04-20 17:03:10	0
1083	API 日志		2	4	108	log	fa:tasks	\N	\N	0	t	t	t		2021-02-26 02:18:24	1	2024-07-09 10:57:39.622	0
1056	生成修改	infra:codegen:update	3	2	2760					0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-09 11:31:37.017	0
1058	导入代码	infra:codegen:create	3	2	2760					0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-09 11:31:46.392	0
1057	生成删除	infra:codegen:delete	3	3	2760					0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-09 11:32:03.36	0
1059	预览代码	infra:codegen:preview	3	4	2760					0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-09 11:32:09.605	0
1060	生成代码	infra:codegen:download	3	5	2760					0	t	t	t	admin	2021-01-05 17:03:48	1	2024-07-09 11:32:15.411	0
1036	公告查询	system:notice:query	3	1	107	#	#		\N	0	t	t	t	admin	2021-01-05 17:03:48		2022-04-20 17:03:10	1
1037	公告新增	system:notice:create	3	2	107				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	1
1038	公告修改	system:notice:update	3	3	107				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	1
1039	公告删除	system:notice:delete	3	4	107				\N	0	t	t	t	admin	2021-01-05 17:03:48	1	2022-04-20 17:03:10	1
1101	短信模板查询	system:sms-template:query	3	1	1100				\N	0	t	t	t		2021-04-01 17:35:17		2022-04-20 17:03:10	1
1102	短信模板创建	system:sms-template:create	3	2	1100				\N	0	t	t	t		2021-04-01 17:35:17		2022-04-20 17:03:10	1
1103	短信模板更新	system:sms-template:update	3	3	1100				\N	0	t	t	t		2021-04-01 17:35:17		2022-04-20 17:03:10	1
1100	短信模板		2	1	1093	sms-template	ep:connection	system/sms/template/index	SystemSmsTemplate	0	t	t	t		2021-04-01 17:35:17	1	2024-02-29 01:16:18	1
1095	短信渠道查询	system:sms-channel:query	3	1	1094				\N	0	t	t	t		2021-04-01 11:07:15		2022-04-20 17:03:10	1
1096	短信渠道创建	system:sms-channel:create	3	2	1094				\N	0	t	t	t		2021-04-01 11:07:15		2022-04-20 17:03:10	1
1097	短信渠道更新	system:sms-channel:update	3	3	1094				\N	0	t	t	t		2021-04-01 11:07:15		2022-04-20 17:03:10	1
1098	短信渠道删除	system:sms-channel:delete	3	4	1094				\N	0	t	t	t		2021-04-01 11:07:15		2022-04-20 17:03:10	1
1094	短信渠道		2	0	1093	sms-channel	fa:stack-exchange	system/sms/channel/index	SystemSmsChannel	0	t	t	t		2021-04-01 11:07:15	1	2024-02-29 01:15:54	1
1093	短信管理		1	1	2739	sms	ep:message	\N	\N	0	t	t	t	1	2021-04-05 01:10:16	1	2024-04-22 23:56:03	1
1138	租户列表		2	0	1224	list	ep:house	system/tenant/index	SystemTenant	0	t	t	t		2021-12-14 12:31:43	1	2024-02-29 01:01:10	0
1139	租户查询	system:tenant:query	3	1	1138				\N	0	t	t	t		2021-12-14 12:31:44		2022-04-20 17:03:10	0
1140	租户创建	system:tenant:create	3	2	1138				\N	0	t	t	t		2021-12-14 12:31:44		2022-04-20 17:03:10	0
1141	租户更新	system:tenant:update	3	3	1138				\N	0	t	t	t		2021-12-14 12:31:44		2022-04-20 17:03:10	0
1092	文件删除	infra:file:delete	3	4	1090				\N	0	t	t	t		2021-03-12 20:16:20		2022-04-20 17:03:10	1
1090	文件列表		2	5	1243	file	ep:upload-filled	infra/file/index	InfraFile	0	t	t	t		2021-03-12 20:16:20	1	2024-02-29 08:53:02	1
1142	租户删除	system:tenant:delete	3	4	1138				\N	0	t	t	t		2021-12-14 12:31:44		2022-04-20 17:03:10	0
1143	租户导出	system:tenant:export	3	5	1138				\N	0	t	t	t		2021-12-14 12:31:44		2022-04-20 17:03:10	0
1128	支付应用信息创建	pay:app:create	3	2	1126				\N	0	t	t	t		2021-11-10 01:13:31		2022-04-20 17:03:10	1
1133	支付商户信息查询	pay:merchant:query	3	1	1132				\N	0	t	t	t		2021-11-10 01:13:41		2022-04-20 17:03:10	1
1162	退款订单查询	pay:refund:query	3	1	1161				\N	0	t	t	t		2021-12-25 08:29:07		2022-04-20 17:03:10	1
1163	退款订单创建	pay:refund:create	3	2	1161				\N	0	t	t	t		2021-12-25 08:29:07		2022-04-20 17:03:10	1
1164	退款订单更新	pay:refund:update	3	3	1161				\N	0	t	t	t		2021-12-25 08:29:07		2022-04-20 17:03:10	1
1165	退款订单删除	pay:refund:delete	3	4	1161				\N	0	t	t	t		2021-12-25 08:29:07		2022-04-20 17:03:10	1
1166	退款订单导出	pay:refund:export	3	5	1161				\N	0	t	t	t		2021-12-25 08:29:07		2022-04-20 17:03:10	1
1161	退款订单		2	3	1117	refund	fa:registered	pay/refund/index	PayRefund	0	t	t	t		2021-12-25 08:29:07	1	2024-02-29 08:59:20	1
1174	支付订单查询	pay:order:query	3	1	1173				\N	0	t	t	t		2021-12-25 08:49:43		2022-04-20 17:03:10	1
1175	支付订单创建	pay:order:create	3	2	1173				\N	0	t	t	t		2021-12-25 08:49:43		2022-04-20 17:03:10	1
1176	支付订单更新	pay:order:update	3	3	1173				\N	0	t	t	t		2021-12-25 08:49:43		2022-04-20 17:03:10	1
1177	支付订单删除	pay:order:delete	3	4	1173				\N	0	t	t	t		2021-12-25 08:49:43		2022-04-20 17:03:10	1
1178	支付订单导出	pay:order:export	3	5	1173				\N	0	t	t	t		2021-12-25 08:49:43		2022-04-20 17:03:10	1
1173	支付订单		2	2	1117	order	fa:cc-paypal	pay/order/index	PayOrder	0	t	t	t		2021-12-25 08:49:43	1	2024-02-29 08:59:43	1
1130	支付应用信息删除	pay:app:delete	3	4	1126				\N	0	t	t	t		2021-11-10 01:13:31		2022-04-20 17:03:10	1
1150	秘钥解析		3	6	1129				\N	0	t	t	t	1	2021-11-08 15:15:47	1	2022-04-20 17:03:10	1
1134	支付商户信息创建	pay:merchant:create	3	2	1132				\N	0	t	t	t		2021-11-10 01:13:41		2022-04-20 17:03:10	1
1135	支付商户信息更新	pay:merchant:update	3	3	1132				\N	0	t	t	t		2021-11-10 01:13:41		2022-04-20 17:03:10	1
1136	支付商户信息删除	pay:merchant:delete	3	4	1132				\N	0	t	t	t		2021-11-10 01:13:41		2022-04-20 17:03:10	1
1137	支付商户信息导出	pay:merchant:export	3	5	1132				\N	0	t	t	t		2021-11-10 01:13:41		2022-04-20 17:03:10	1
1132	秘钥解析	pay:channel:parsing	3	6	1129				\N	0	t	t	t	1	2021-11-08 15:15:47	1	2022-04-20 17:03:10	1
1129	支付应用信息更新	pay:app:update	3	3	1126				\N	0	t	t	t		2021-11-10 01:13:31		2022-04-20 17:03:10	1
1126	应用信息		2	1	1117	app	fa:apple	pay/app/index	PayApp	0	t	t	t		2021-11-10 01:13:30	1	2024-02-29 08:59:55	1
1117	支付管理		1	30	0	/pay	ep:money	\N	\N	0	t	t	t	1	2021-12-25 16:43:41	1	2024-02-29 08:58:38	1
1119	请假申请查询	bpm:oa-leave:query	3	1	1118				\N	0	t	t	t		2021-09-20 08:51:03	1	2022-04-20 17:03:10	1
1120	请假申请创建	bpm:oa-leave:create	3	2	1118				\N	0	t	t	t		2021-09-20 08:51:03	1	2022-04-20 17:03:10	1
1118	请假查询		2	0	5	leave	fa:leanpub	bpm/oa/leave/index	BpmOALeave	0	t	t	t		2021-09-20 08:51:03	1	2024-02-29 12:38:21	1
1188	表单查询	bpm:form:query	3	1	1187				\N	0	t	t	t		2021-12-30 12:38:22	1	2022-04-20 17:03:10	1
1189	表单创建	bpm:form:create	3	2	1187				\N	0	t	t	t		2021-12-30 12:38:22	1	2022-04-20 17:03:10	1
1190	表单更新	bpm:form:update	3	3	1187				\N	0	t	t	t		2021-12-30 12:38:22	1	2022-04-20 17:03:10	1
1191	表单删除	bpm:form:delete	3	4	1187				\N	0	t	t	t		2021-12-30 12:38:22	1	2022-04-20 17:03:10	1
1187	流程表单		2	2	1186	form	fa:hdd-o	bpm/form/index	BpmForm	0	t	t	t		2021-12-30 12:38:22	1	2024-03-19 12:25:25	1
1194	模型查询	bpm:model:query	3	1	1193				\N	0	t	t	t	1	2022-01-03 19:01:10	1	2022-04-20 17:03:10	1
1195	模型创建	bpm:model:create	3	2	1193				\N	0	t	t	t	1	2022-01-03 19:01:24	1	2022-04-20 17:03:10	1
1196	模型导入	bpm:model:import	3	3	1193				\N	0	t	t	t	1	2022-01-03 19:01:35	1	2022-04-20 17:03:10	1
1197	模型更新	bpm:model:update	3	4	1193				\N	0	t	t	t	1	2022-01-03 19:02:28	1	2022-04-20 17:03:10	1
1198	模型删除	bpm:model:delete	3	5	1193				\N	0	t	t	t	1	2022-01-03 19:02:43	1	2022-04-20 17:03:10	1
1199	模型发布	bpm:model:deploy	3	6	1193				\N	0	t	t	t	1	2022-01-03 19:03:24	1	2022-04-20 17:03:10	1
1193	流程模型		2	1	1186	model	fa-solid:project-diagram	bpm/model/index	BpmModel	0	t	t	t	1	2021-12-31 23:24:58	1	2024-03-19 12:25:19	1
1186	流程管理		1	10	1185	manager	fa:dedent	\N	\N	0	t	t	t	1	2021-12-30 20:28:30	1	2024-02-29 12:36:02	1
1185	工作流程		1	50	0	/bpm	fa:medium	\N	\N	0	t	t	t	1	2021-12-30 20:26:36	1	2024-02-29 12:43:43	1
1108	短信日志查询	system:sms-log:query	3	1	1107				\N	0	t	t	t		2021-04-11 08:37:05		2022-04-20 17:03:10	1
1109	短信日志导出	system:sms-log:export	3	5	1107				\N	0	t	t	t		2021-04-11 08:37:05		2022-04-20 17:03:10	1
1107	短信日志		2	2	1093	sms-log	fa:edit	system/sms/log/index	SystemSmsLog	0	t	t	t		2021-04-11 08:37:05	1	2024-02-29 08:49:02	1
1104	短信模板删除	system:sms-template:delete	3	4	1100				\N	0	t	t	t		2021-04-01 17:35:17		2022-04-20 17:03:10	1
1105	短信模板导出	system:sms-template:export	3	5	1100				\N	0	t	t	t		2021-04-01 17:35:17		2022-04-20 17:03:10	1
1106	发送测试短信	system:sms-template:send-sms	3	6	1100				\N	0	t	t	t	1	2021-04-11 00:26:40	1	2022-04-20 17:03:10	1
1225	租户套餐		2	0	1224	package	fa:bars	system/tenantPackage/index	SystemTenantPackage	0	t	t	t		2022-02-19 17:44:06	1	2024-02-29 01:01:43	0
1226	租户套餐查询	system:tenant-package:query	3	1	1225				\N	0	t	t	t		2022-02-19 17:44:06		2022-04-20 17:03:10	0
1227	租户套餐创建	system:tenant-package:create	3	2	1225				\N	0	t	t	t		2022-02-19 17:44:06		2022-04-20 17:03:10	0
1228	租户套餐更新	system:tenant-package:update	3	3	1225				\N	0	t	t	t		2022-02-19 17:44:06		2022-04-20 17:03:10	0
1229	租户套餐删除	system:tenant-package:delete	3	4	1225				\N	0	t	t	t		2022-02-19 17:44:06		2022-04-20 17:03:10	0
2020	规格查询	product:property:query	3	1	2019				\N	0	t	t	t		2022-08-01 14:55:35		2022-12-12 20:26:24	1
1256	数据源配置查询	infra:data-source-config:query	3	1	1255				\N	0	t	t	t		2022-04-27 14:37:32		2022-04-27 14:37:32	0
1257	数据源配置创建	infra:data-source-config:create	3	2	1255				\N	0	t	t	t		2022-04-27 14:37:32		2022-04-27 14:37:32	0
1258	数据源配置更新	infra:data-source-config:update	3	3	1255				\N	0	t	t	t		2022-04-27 14:37:32		2022-04-27 14:37:32	0
1259	数据源配置删除	infra:data-source-config:delete	3	4	1255				\N	0	t	t	t		2022-04-27 14:37:32		2022-04-27 14:37:32	0
1260	数据源配置导出	infra:data-source-config:export	3	5	1255				\N	0	t	t	t		2022-04-27 14:37:32		2022-04-27 14:37:32	0
1263	应用管理		2	0	1261	oauth2/application	fa:hdd-o	system/oauth2/client/index	SystemOAuth2Client	0	t	t	t		2022-05-10 16:26:33	1	2024-02-29 01:13:14	0
1264	客户端查询	system:oauth2-client:query	3	1	1263				\N	0	t	t	t		2022-05-10 16:26:33	1	2022-05-11 00:31:06	0
1265	客户端创建	system:oauth2-client:create	3	2	1263				\N	0	t	t	t		2022-05-10 16:26:33	1	2022-05-11 00:31:23	0
1266	客户端更新	system:oauth2-client:update	3	3	1263				\N	0	t	t	t		2022-05-10 16:26:33	1	2022-05-11 00:31:28	0
1267	客户端删除	system:oauth2-client:delete	3	4	1263				\N	0	t	t	t		2022-05-10 16:26:33	1	2022-05-11 00:31:33	0
1282	报表设计器		2	1	1281	jimu-report	ep:trend-charts	report/jmreport/index	GoView	0	t	t	t	1	2022-07-10 20:26:36	1	2024-02-29 12:33:54	1
1281	报表管理		2	40	0	/report	ep:pie-chart	\N	\N	0	t	t	t	1	2022-07-10 20:22:15	1	2024-02-29 12:33:03	1
1208	已办任务		2	20	1200	done	fa:delicious	bpm/task/done/index	BpmDoneTask	0	t	t	t	1	2022-01-08 10:34:13	1	2024-02-29 12:37:54	1
1221	流程任务的查询	bpm:task:query	3	1	1207				\N	0	t	t	t	1	2022-01-23 00:38:52	1	2022-04-20 17:03:10	1
1222	流程任务的更新	bpm:task:update	3	2	1207				\N	0	t	t	t	1	2022-01-23 00:39:24	1	2022-04-20 17:03:10	1
1207	待办任务		2	10	1200	todo	fa:slack	bpm/task/todo/index	BpmTodoTask	0	t	t	t	1	2022-01-08 10:33:37	1	2024-02-29 12:37:39	1
1202	流程实例的查询	bpm:process-instance:query	3	1	1201				\N	0	t	t	t		2022-01-07 15:53:44	1	2022-04-20 17:03:10	1
1219	流程实例的创建	bpm:process-instance:create	3	2	1201				\N	0	t	t	t	1	2022-01-23 00:36:15	1	2022-04-20 17:03:10	1
1220	流程实例的取消	bpm:process-instance:cancel	3	3	1201				\N	0	t	t	t	1	2022-01-23 00:36:33	1	2022-04-20 17:03:10	1
1201	我的流程		2	1	1200	my	fa-solid:book	bpm/processInstance/index	BpmProcessInstanceMy	0	t	t	t		2022-01-07 15:53:44	1	2024-03-21 23:52:12	1
1210	用户组查询	bpm:user-group:query	3	1	1209				\N	0	t	t	t		2022-01-14 02:14:20		2022-04-20 17:03:10	1
1211	用户组创建	bpm:user-group:create	3	2	1209				\N	0	t	t	t		2022-01-14 02:14:20		2022-04-20 17:03:10	1
1212	用户组更新	bpm:user-group:update	3	3	1209				\N	0	t	t	t		2022-01-14 02:14:20		2022-04-20 17:03:10	1
1213	用户组删除	bpm:user-group:delete	3	4	1209				\N	0	t	t	t		2022-01-14 02:14:20		2022-04-20 17:03:10	1
1209	用户分组		2	4	1186	user-group	fa:user-secret	bpm/group/index	BpmUserGroup	0	t	t	t		2022-01-14 02:14:20	1	2024-03-21 23:55:29	1
1215	流程定义查询	bpm:process-definition:query	3	10	1193				\N	0	t	t	t	1	2022-01-23 00:21:43	1	2022-04-20 17:03:10	1
1216	流程任务分配规则查询	bpm:task-assign-rule:query	3	20	1193				\N	0	t	t	t	1	2022-01-23 00:26:53	1	2022-04-20 17:03:10	1
1217	流程任务分配规则创建	bpm:task-assign-rule:create	3	21	1193				\N	0	t	t	t	1	2022-01-23 00:28:15	1	2022-04-20 17:03:10	1
1218	流程任务分配规则更新	bpm:task-assign-rule:update	3	22	1193				\N	0	t	t	t	1	2022-01-23 00:28:41	1	2022-04-20 17:03:10	1
2003	分类查询	product:category:query	3	1	2002				\N	0	t	t	t		2022-07-29 15:53:53		2022-07-29 15:53:53	1
2004	分类创建	product:category:create	3	2	2002				\N	0	t	t	t		2022-07-29 15:53:53		2022-07-29 15:53:53	1
2005	分类更新	product:category:update	3	3	2002				\N	0	t	t	t		2022-07-29 15:53:53		2022-07-29 15:53:53	1
2006	分类删除	product:category:delete	3	4	2002				\N	0	t	t	t		2022-07-29 15:53:53		2022-07-29 15:53:53	1
2002	商品分类		2	2	2000	category	ep:cellphone	mall/product/category/index	ProductCategory	0	t	t	t		2022-07-29 15:53:53	1	2023-08-21 10:27:15	1
2000	商品中心		1	60	2362	product	fa:product-hunt	\N	\N	0	t	t	t		2022-07-29 15:53:53	1	2023-09-30 11:52:36	1
1261	客户端管理		2	10	1	oauth2	fa:dashcube	\N	\N	0	t	t	t	1	2022-05-09 23:38:17	1	2024-07-09 10:52:20.611	0
1224	租户管理		1	0	1	/tenant	fa-solid:house-user	\N	\N	0	t	t	t	1	2022-02-20 01:41:13	1	2024-07-09 11:41:13.426	0
2016	商品创建	product:spu:create	3	2	2014				\N	0	t	t	t		2022-07-30 14:22:58		2022-07-30 14:22:58	1
2017	商品更新	product:spu:update	3	3	2014				\N	0	t	t	t		2022-07-30 14:22:58		2022-07-30 14:22:58	1
2018	商品删除	product:spu:delete	3	4	2014				\N	0	t	t	t		2022-07-30 14:22:58		2022-07-30 14:22:58	1
2014	商品列表		2	1	2000	spu	ep:apple	mall/product/spu/index	ProductSpu	0	t	t	t		2022-07-30 14:22:58	1	2023-08-21 10:27:01	1
2009	品牌查询	product:brand:query	3	1	2008				\N	0	t	t	t		2022-07-30 13:52:44		2022-07-30 13:52:44	1
2010	品牌创建	product:brand:create	3	2	2008				\N	0	t	t	t		2022-07-30 13:52:44		2022-07-30 13:52:44	1
2011	品牌更新	product:brand:update	3	3	2008				\N	0	t	t	t		2022-07-30 13:52:44		2022-07-30 13:52:44	1
2012	品牌删除	product:brand:delete	3	4	2008				\N	0	t	t	t		2022-07-30 13:52:44		2022-07-30 13:52:44	1
2021	规格创建	product:property:create	3	2	2019				\N	0	t	t	t		2022-08-01 14:55:35		2022-12-12 20:26:30	1
1239	文件配置创建	infra:file-config:create	3	2	1237				\N	0	t	t	t		2022-03-15 14:35:28		2022-04-20 17:03:10	1
1240	文件配置更新	infra:file-config:update	3	3	1237				\N	0	t	t	t		2022-03-15 14:35:28		2022-04-20 17:03:10	1
1241	文件配置删除	infra:file-config:delete	3	4	1237				\N	0	t	t	t		2022-03-15 14:35:28		2022-04-20 17:03:10	1
1242	文件配置导出	infra:file-config:export	3	5	1237				\N	0	t	t	t		2022-03-15 14:35:28		2022-04-20 17:03:10	1
1237	文件配置		2	0	1243	file-config	fa-solid:file-signature	infra/fileConfig/index	InfraFileConfig	0	t	t	t		2022-03-15 14:35:28	1	2024-02-29 08:52:54	1
2022	规格更新	product:property:update	3	3	2019				\N	0	t	t	t		2022-08-01 14:55:35		2022-12-12 20:26:33	1
2023	规格删除	product:property:delete	3	4	2019				\N	0	t	t	t		2022-08-01 14:55:35		2022-12-12 20:26:37	1
2569	产品删除	erp:product:delete	3	4	2565					0	t	t	t		2024-02-04 07:52:15	1	2024-02-04 17:22:22	1
2019	商品属性		2	4	2000	property	ep:cold-drink	mall/product/property/index	ProductProperty	0	t	t	t		2022-08-01 14:55:35	1	2023-08-26 11:01:05	1
2026	Banner查询	promotion:banner:query	3	1	2025					0	t	t	t		2022-08-01 14:56:14	1	2023-10-24 20:20:18	1
2027	Banner创建	promotion:banner:create	3	2	2025					0	t	t	t		2022-08-01 14:56:14	1	2023-10-24 20:20:23	1
2028	Banner更新	promotion:banner:update	3	3	2025					0	t	t	t		2022-08-01 14:56:14	1	2023-10-24 20:20:28	1
2029	Banner删除	promotion:banner:delete	3	4	2025					0	t	t	t		2022-08-01 14:56:14	1	2023-10-24 20:20:36	1
2025	Banner		2	100	2387	banner	fa:bandcamp	mall/promotion/banner/index	\N	0	t	t	t		2022-08-01 14:56:14	1	2023-10-24 20:20:06	1
2033	优惠劵模板查询	promotion:coupon-template:query	3	1	2032				\N	0	t	t	t		2022-10-31 22:27:14		2022-10-31 22:27:14	1
2034	优惠劵模板创建	promotion:coupon-template:create	3	2	2032				\N	0	t	t	t		2022-10-31 22:27:14		2022-10-31 22:27:14	1
2035	优惠劵模板更新	promotion:coupon-template:update	3	3	2032				\N	0	t	t	t		2022-10-31 22:27:14		2022-10-31 22:27:14	1
2036	优惠劵模板删除	promotion:coupon-template:delete	3	4	2032				\N	0	t	t	t		2022-10-31 22:27:14		2022-10-31 22:27:14	1
2032	优惠劵列表		2	1	2365	template	ep:discount	mall/promotion/coupon/template/index	PromotionCouponTemplate	0	t	t	t		2022-10-31 22:27:14	1	2023-10-03 12:40:06	1
2039	优惠劵查询	promotion:coupon:query	3	1	2038				\N	0	t	t	t		2022-11-03 23:21:31		2022-11-03 23:21:31	1
2040	优惠劵删除	promotion:coupon:delete	3	4	2038				\N	0	t	t	t		2022-11-03 23:21:31		2022-11-03 23:21:31	1
2038	领取记录		2	2	2365	list	ep:collection-tag	mall/promotion/coupon/index	PromotionCoupon	0	t	t	t		2022-11-03 23:21:31	1	2023-10-03 12:55:30	1
2067	秒杀时段查询	promotion:seckill-config:query	3	1	2066					0	t	t	t		2022-11-15 19:46:51	1	2023-06-24 17:50:25	1
2068	秒杀时段创建	promotion:seckill-config:create	3	2	2066					0	t	t	t		2022-11-15 19:46:51	1	2023-06-24 17:48:39	1
2069	秒杀时段更新	promotion:seckill-config:update	3	3	2066					0	t	t	t		2022-11-15 19:46:51	1	2023-06-24 17:50:29	1
2070	秒杀时段删除	promotion:seckill-config:delete	3	4	2066					0	t	t	t		2022-11-15 19:46:51	1	2023-06-24 17:50:32	1
2066	秒杀时段		2	1	2209	config	ep:baseball	mall/promotion/seckill/config/index	PromotionSeckillConfig	0	t	t	t		2022-11-15 19:46:50	1	2023-06-24 18:57:14	1
2060	秒杀活动查询	promotion:seckill-activity:query	3	1	2059				\N	0	t	t	t		2022-11-06 22:24:49		2022-11-06 22:24:49	1
2061	秒杀活动创建	promotion:seckill-activity:create	3	2	2059				\N	0	t	t	t		2022-11-06 22:24:49		2022-11-06 22:24:49	1
2062	秒杀活动更新	promotion:seckill-activity:update	3	3	2059				\N	0	t	t	t		2022-11-06 22:24:49		2022-11-06 22:24:49	1
2063	秒杀活动删除	promotion:seckill-activity:delete	3	4	2059				\N	0	t	t	t		2022-11-06 22:24:49		2022-11-06 22:24:49	1
2048	限时折扣活动查询	promotion:discount-activity:query	3	1	2047				\N	0	t	t	t		2022-11-05 17:12:15		2022-11-05 17:12:15	1
2049	限时折扣活动创建	promotion:discount-activity:create	3	2	2047				\N	0	t	t	t		2022-11-05 17:12:15		2022-11-05 17:12:15	1
2050	限时折扣活动更新	promotion:discount-activity:update	3	3	2047				\N	0	t	t	t		2022-11-05 17:12:16		2022-11-05 17:12:16	1
2051	限时折扣活动删除	promotion:discount-activity:delete	3	4	2047				\N	0	t	t	t		2022-11-05 17:12:16		2022-11-05 17:12:16	1
2052	限时折扣活动关闭	promotion:discount-activity:close	3	5	2047				\N	0	t	t	t		2022-11-05 17:12:16		2022-11-05 17:12:16	1
2042	满减送活动查询	promotion:reward-activity:query	3	1	2041				\N	0	t	t	t		2022-11-04 23:47:49		2022-11-04 23:47:49	1
2044	满减送活动更新	promotion:reward-activity:update	3	3	2041				\N	0	t	t	t		2022-11-04 23:47:50		2022-11-04 23:47:50	1
2043	满减送活动创建	promotion:reward-activity:create	3	2	2041				\N	0	t	t	t		2022-11-04 23:47:49		2022-11-04 23:47:49	1
2045	满减送活动删除	promotion:reward-activity:delete	3	4	2041				\N	0	t	t	t		2022-11-04 23:47:50		2022-11-04 23:47:50	1
2046	满减送活动关闭	promotion:reward-activity:close	3	5	2041				\N	0	t	t	t	1	2022-11-05 10:42:53	1	2022-11-05 10:42:53	1
2041	满减送		2	10	2390	reward-activity	ep:goblet-square-full	mall/promotion/rewardActivity/index	PromotionRewardActivity	0	t	t	t		2022-11-04 23:47:49	1	2023-10-21 19:24:46	1
2047	限时折扣		2	7	2390	discount-activity	ep:timer	mall/promotion/discountActivity/index	PromotionDiscountActivity	0	t	t	t		2022-11-05 17:12:15	1	2023-10-21 19:24:21	1
2030	营销中心		1	70	2362	promotion	ep:present	\N	\N	0	t	t	t	1	2022-10-31 21:25:09	1	2023-09-30 11:54:27	1
2083	地区管理		2	14	1	area	fa:map-marker	system/area/index	SystemArea	0	t	t	t	1	2022-12-23 17:35:05	1	2024-02-29 08:50:28	0
2074	售后查询	trade:after-sale:query	3	1	2073				\N	0	t	t	t		2022-11-19 20:15:33	1	2022-12-10 21:04:29	1
2073	售后退款		2	2	2072	after-sale	ep:refrigerator	mall/trade/afterSale/index	TradeAfterSale	0	t	t	t		2022-11-19 20:15:32	1	2023-10-01 21:42:21	1
2072	订单中心		1	65	2362	trade	ep:eleme	\N	\N	0	t	t	t	1	2022-11-19 18:57:19	1	2023-09-30 11:54:07	1
2075	秒杀活动关闭	promotion:seckill-activity:close	3	5	2059					0	t	t	t	1	2022-11-28 20:20:15	1	2023-10-03 18:34:28	1
2092	数据统计	mp:statistics:query	2	2	2084	statistics	ep:trend-charts	mp/statistics/index	MpStatistics	0	t	t	t	1	2023-01-07 20:17:36	1	2024-02-29 12:42:21	1
2088	查询账号	mp:account:query	3	0	2085				\N	0	t	t	t	1	2023-01-07 17:33:07	1	2023-01-07 17:33:07	1
2086	新增账号	mp:account:create	3	1	2085				\N	0	t	t	t	1	2023-01-01 20:21:40	1	2023-01-07 17:32:53	1
2087	修改账号	mp:account:update	3	2	2085				\N	0	t	t	t	1	2023-01-07 17:32:46	1	2023-01-07 17:32:46	1
2089	删除账号	mp:account:delete	3	3	2085				\N	0	t	t	t	1	2023-01-07 17:33:21	1	2023-01-07 17:33:21	1
2090	生成二维码	mp:account:qr-code	3	4	2085				\N	0	t	t	t	1	2023-01-07 17:33:58	1	2023-01-07 17:33:58	1
2091	清空 API 配额	mp:account:clear-quota	3	5	2085				\N	0	t	t	t	1	2023-01-07 18:20:32	1	2023-01-07 18:20:59	1
2566	产品查询	erp:product:query	3	1	2565					0	t	t	t		2024-02-04 07:52:15	1	2024-02-04 17:21:57	1
2085	账号管理		2	1	2084	account	fa:user	mp/account/index	MpAccount	0	t	t	t	1	2023-01-01 20:13:31	1	2024-02-29 12:42:10	1
2094	查询标签	mp:tag:query	3	0	2093				\N	0	t	t	t	1	2023-01-08 11:59:03	1	2023-01-08 11:59:03	1
2095	新增标签	mp:tag:create	3	1	2093				\N	0	t	t	t	1	2023-01-08 11:59:23	1	2023-01-08 11:59:23	1
2096	修改标签	mp:tag:update	3	2	2093				\N	0	t	t	t	1	2023-01-08 11:59:41	1	2023-01-08 11:59:41	1
2097	删除标签	mp:tag:delete	3	3	2093				\N	0	t	t	t	1	2023-01-08 12:00:04	1	2023-01-08 12:00:13	1
2098	同步标签	mp:tag:sync	3	4	2093				\N	0	t	t	t	1	2023-01-08 12:00:29	1	2023-01-08 12:00:29	1
2100	查询粉丝	mp:user:query	3	0	2099				\N	0	t	t	t	1	2023-01-08 17:16:59	1	2023-01-08 17:17:23	1
2101	修改粉丝	mp:user:update	3	1	2099				\N	0	t	t	t	1	2023-01-08 17:17:11	1	2023-01-08 17:17:11	1
2102	同步粉丝	mp:user:sync	3	2	2099				\N	0	t	t	t	1	2023-01-08 17:17:40	1	2023-01-08 17:17:40	1
2099	粉丝管理		2	4	2084	user	fa:user-secret	mp/user/index	MpUser	0	t	t	t	1	2023-01-08 16:51:20	1	2024-02-29 12:42:39	1
2128	查询消息	mp:message:query	3	0	2103				\N	0	t	t	t	1	2023-01-17 23:07:14	1	2023-01-17 23:07:14	1
2129	发送消息	mp:message:send	3	1	2103				\N	0	t	t	t	1	2023-01-17 23:07:26	1	2023-01-17 23:07:26	1
2103	消息管理		2	5	2084	message	ep:message	mp/message/index	MpMessage	0	t	t	t	1	2023-01-08 18:44:19	1	2024-02-29 12:42:50	1
2125	查询菜单	mp:menu:query	3	0	2119				\N	0	t	t	t	1	2023-01-17 23:05:41	1	2023-01-17 23:05:41	1
2126	保存菜单	mp:menu:save	3	1	2119				\N	0	t	t	t	1	2023-01-17 23:06:01	1	2023-01-17 23:06:01	1
2127	删除菜单	mp:menu:delete	3	2	2119				\N	0	t	t	t	1	2023-01-17 23:06:16	1	2023-01-17 23:06:16	1
2119	菜单管理		2	6	2084	menu	ep:menu	mp/menu/index	MpMenu	0	t	t	t	1	2023-01-14 17:43:54	1	2024-02-29 12:42:56	1
2121	查询回复	mp:auto-reply:query	3	0	2120				\N	0	t	t	t	1	2023-01-16 22:28:41	1	2023-01-16 22:28:41	1
2122	新增回复	mp:auto-reply:create	3	1	2120				\N	0	t	t	t	1	2023-01-16 22:28:54	1	2023-01-16 22:28:54	1
2123	修改回复	mp:auto-reply:update	3	2	2120				\N	0	t	t	t	1	2023-01-16 22:29:05	1	2023-01-16 22:29:05	1
2124	删除回复	mp:auto-reply:delete	3	3	2120				\N	0	t	t	t	1	2023-01-16 22:29:34	1	2023-01-16 22:29:34	1
2120	自动回复		2	7	2084	auto-reply	fa-solid:republican	mp/autoReply/index	MpAutoReply	0	t	t	t	1	2023-01-15 22:13:09	1	2024-02-29 12:43:10	1
2114	上传临时素材	mp:material:upload-temporary	3	1	2113				\N	0	t	t	t	1	2023-01-14 15:33:55	1	2023-01-14 15:33:55	1
2115	上传永久素材	mp:material:upload-permanent	3	2	2113				\N	0	t	t	t	1	2023-01-14 15:34:14	1	2023-01-14 15:34:14	1
2116	删除素材	mp:material:delete	3	3	2113				\N	0	t	t	t	1	2023-01-14 15:35:37	1	2023-01-14 15:35:37	1
2117	上传图文图片	mp:material:upload-news-image	3	4	2113				\N	0	t	t	t	1	2023-01-14 15:36:31	1	2023-01-14 15:36:31	1
2118	查询素材	mp:material:query	3	5	2113				\N	0	t	t	t	1	2023-01-14 15:39:22	1	2023-01-14 15:39:22	1
2113	素材管理		2	8	2084	material	ep:basketball	mp/material/index	MpMaterial	0	t	t	t	1	2023-01-14 14:12:07	1	2024-02-29 12:43:18	1
2111	查询草稿	mp:draft:query	3	0	2108				\N	0	t	t	t	1	2023-01-14 10:09:01	1	2023-01-14 10:09:01	1
2109	新建草稿	mp:draft:create	3	1	2108				\N	0	t	t	t	1	2023-01-13 23:15:30	1	2023-01-13 23:15:44	1
2110	修改草稿	mp:draft:update	3	2	2108				\N	0	t	t	t	1	2023-01-14 10:08:47	1	2023-01-14 10:08:47	1
2112	删除草稿	mp:draft:delete	3	3	2108				\N	0	t	t	t	1	2023-01-14 10:09:19	1	2023-01-14 10:09:19	1
2108	图文草稿箱		2	9	2084	draft	ep:edit	mp/draft/index	MpDraft	0	t	t	t	1	2023-01-13 07:40:21	1	2024-02-29 12:43:26	1
2105	查询发布列表	mp:free-publish:query	3	1	2104				\N	0	t	t	t	1	2023-01-13 07:19:17	1	2023-01-13 07:19:17	1
2106	发布草稿	mp:free-publish:submit	3	2	2104				\N	0	t	t	t	1	2023-01-13 07:19:46	1	2023-01-13 07:19:46	1
2107	删除发布记录	mp:free-publish:delete	3	3	2104				\N	0	t	t	t	1	2023-01-13 07:20:01	1	2023-01-13 07:20:01	1
2084	公众号管理		1	100	0	/mp	ep:compass	\N	\N	0	t	t	t	1	2023-01-01 20:11:04	1	2024-02-29 12:39:30	1
2132	账号查询	system:mail-account:query	3	1	2131				\N	0	t	t	t		2023-01-25 09:33:48		2023-01-25 09:33:48	1
2131	邮箱账号		2	0	2130	mail-account	fa:universal-access	system/mail/account/index	SystemMailAccount	0	t	t	t		2023-01-25 09:33:48	1	2024-02-29 08:48:16	1
2130	邮箱管理		2	2	2739	mail	fa-solid:mail-bulk	\N	\N	0	t	t	t	1	2023-01-25 17:27:44	1	2024-04-22 23:56:08	1
2160	Cloud 开发文档		1	2	0	https://cloud.iocoder.cn	ep:document-copy	\N	\N	0	t	t	t	1	2023-02-10 22:47:07	1	2023-12-02 21:32:29	1
2161	接入示例		1	99	1117	demo	fa-solid:dragon	pay/demo/index	\N	0	t	t	t		2023-02-11 14:21:42	1	2024-01-18 23:50:00	1
2156	查询项目	report:go-view-project:query	3	0	2153				\N	0	t	t	t	1	2023-02-07 19:25:53	1	2023-02-07 19:25:53	1
2154	创建项目	report:go-view-project:create	3	1	2153				\N	0	t	t	t	1	2023-02-07 19:25:14	1	2023-02-07 19:25:14	1
2155	更新项目	report:go-view-project:update	3	2	2153					0	t	t	t	1	2023-02-07 19:25:34	1	2024-04-24 20:01:18	1
2157	使用 SQL 查询数据	report:go-view-data:get-by-sql	3	3	2153				\N	0	t	t	t	1	2023-02-07 19:26:15	1	2023-02-07 19:26:15	1
2158	使用 HTTP 查询数据	report:go-view-data:get-by-http	3	4	2153				\N	0	t	t	t	1	2023-02-07 19:26:35	1	2023-02-07 19:26:35	1
2153	大屏设计器		2	2	1281	go-view	fa:area-chart	report/goview/index	JimuReport	0	t	t	t	1	2023-02-07 00:03:19	1	2024-02-29 12:34:02	1
2162	商品导出	product:spu:export	3	5	2014				\N	0	t	t	t		2022-07-30 14:22:58		2022-07-30 14:22:58	1
2168	快递公司查询	trade:delivery:express:query	3	1	2167				\N	0	t	t	t		2023-05-18 09:37:53		2023-05-18 09:37:53	1
2169	快递公司创建	trade:delivery:express:create	3	2	2167				\N	0	t	t	t		2023-05-18 09:37:53		2023-05-18 09:37:53	1
2170	快递公司更新	trade:delivery:express:update	3	3	2167				\N	0	t	t	t		2023-05-18 09:37:53		2023-05-18 09:37:53	1
2171	快递公司删除	trade:delivery:express:delete	3	4	2167				\N	0	t	t	t		2023-05-18 09:37:53		2023-05-18 09:37:53	1
2172	快递公司导出	trade:delivery:express:export	3	5	2167				\N	0	t	t	t		2023-05-18 09:37:53		2023-05-18 09:37:53	1
2326	会员等级查询	member:level:query	3	1	2325				\N	0	t	t	t		2023-08-22 12:41:02		2023-08-22 12:41:02	1
2167	快递公司		2	0	2165	express	ep:compass	mall/trade/delivery/express/index	Express	0	t	t	t	1	2023-05-18 09:27:21	1	2023-08-30 21:02:59	1
2570	产品导出	erp:product:export	3	5	2565					0	t	t	t		2024-02-04 07:52:15	1	2024-02-04 17:22:26	1
2174	快递运费模板查询	trade:delivery:express-template:query	3	1	2173				\N	0	t	t	t		2023-05-20 06:49:53		2023-05-20 06:49:53	1
2175	快递运费模板创建	trade:delivery:express-template:create	3	2	2173				\N	0	t	t	t		2023-05-20 06:49:53		2023-05-20 06:49:53	1
2176	快递运费模板更新	trade:delivery:express-template:update	3	3	2173				\N	0	t	t	t		2023-05-20 06:49:53		2023-05-20 06:49:53	1
2177	快递运费模板删除	trade:delivery:express-template:delete	3	4	2173				\N	0	t	t	t		2023-05-20 06:49:53		2023-05-20 06:49:53	1
2178	快递运费模板导出	trade:delivery:express-template:export	3	5	2173				\N	0	t	t	t		2023-05-20 06:49:53		2023-05-20 06:49:53	1
2165	快递发货		1	0	2164	express	ep:bicycle			0	t	t	t	1	2023-05-18 09:22:06	1	2023-08-30 21:02:49	1
2180	自提门店查询	trade:delivery:pick-up-store:query	3	1	2179				\N	0	t	t	t		2023-05-25 10:53:29		2023-05-25 10:53:29	1
2181	自提门店创建	trade:delivery:pick-up-store:create	3	2	2179				\N	0	t	t	t		2023-05-25 10:53:29		2023-05-25 10:53:29	1
2182	自提门店更新	trade:delivery:pick-up-store:update	3	3	2179				\N	0	t	t	t		2023-05-25 10:53:29		2023-05-25 10:53:29	1
2183	自提门店删除	trade:delivery:pick-up-store:delete	3	4	2179				\N	0	t	t	t		2023-05-25 10:53:29		2023-05-25 10:53:29	1
2179	门店管理		2	1	2166	pick-up-store	ep:basketball	mall/trade/delivery/pickUpStore/index	PickUpStore	0	t	t	t	1	2023-05-25 10:50:00	1	2023-08-30 21:03:28	1
2166	门店自提		1	1	2164	pick-up-store	ep:add-location			0	t	t	t	1	2023-05-18 09:23:14	1	2023-08-30 21:03:21	1
2164	配送管理		1	3	2072	delivery	ep:shopping-cart			0	t	t	t	1	2023-05-18 09:18:02	1	2023-09-28 10:58:09	1
2146	站内信模板查询	system:notify-template:query	3	1	2145				\N	0	t	t	t		2023-01-28 02:26:42		2023-01-28 02:26:42	1
2147	站内信模板创建	system:notify-template:create	3	2	2145				\N	0	t	t	t		2023-01-28 02:26:42		2023-01-28 02:26:42	1
2148	站内信模板更新	system:notify-template:update	3	3	2145				\N	0	t	t	t		2023-01-28 02:26:42		2023-01-28 02:26:42	1
2149	站内信模板删除	system:notify-template:delete	3	4	2145				\N	0	t	t	t		2023-01-28 02:26:42		2023-01-28 02:26:42	1
2150	发送测试站内信	system:notify-template:send-notify	3	5	2145				\N	0	t	t	t	1	2023-01-28 10:54:43	1	2023-01-28 10:54:43	1
2145	模板管理		2	0	2144	notify-template	fa:archive	system/notify/template/index	SystemNotifyTemplate	0	t	t	t		2023-01-28 02:26:42	1	2024-02-29 08:49:14	1
2152	站内信消息查询	system:notify-message:query	3	1	2151				\N	0	t	t	t		2023-01-28 04:28:22		2023-01-28 04:28:22	1
2151	消息记录		2	0	2144	notify-message	fa:edit	system/notify/message/index	SystemNotifyMessage	0	t	t	t		2023-01-28 04:28:22	1	2024-02-29 08:49:22	1
2144	站内信管理		1	3	2739	notify	ep:message-box	\N	\N	0	t	t	t	1	2023-01-28 10:25:18	1	2024-04-22 23:56:12	1
2133	账号创建	system:mail-account:create	3	2	2131				\N	0	t	t	t		2023-01-25 09:33:48		2023-01-25 09:33:48	1
2134	账号更新	system:mail-account:update	3	3	2131				\N	0	t	t	t		2023-01-25 09:33:48		2023-01-25 09:33:48	1
2135	账号删除	system:mail-account:delete	3	4	2131				\N	0	t	t	t		2023-01-25 09:33:48		2023-01-25 09:33:48	1
2137	模版查询	system:mail-template:query	3	1	2136				\N	0	t	t	t		2023-01-25 12:05:31		2023-01-25 12:05:31	1
2138	模版创建	system:mail-template:create	3	2	2136				\N	0	t	t	t		2023-01-25 12:05:31		2023-01-25 12:05:31	1
2139	模版更新	system:mail-template:update	3	3	2136				\N	0	t	t	t		2023-01-25 12:05:31		2023-01-25 12:05:31	1
2140	模版删除	system:mail-template:delete	3	4	2136				\N	0	t	t	t		2023-01-25 12:05:31		2023-01-25 12:05:31	1
2143	发送测试邮件	system:mail-template:send-mail	3	5	2136				\N	0	t	t	t	1	2023-01-26 23:29:15	1	2023-01-26 23:29:15	1
2136	邮件模版		2	0	2130	mail-template	fa:tag	system/mail/template/index	SystemMailTemplate	0	t	t	t		2023-01-25 12:05:31	1	2024-02-29 08:48:41	1
2142	日志查询	system:mail-log:query	3	1	2141				\N	0	t	t	t		2023-01-26 02:16:50		2023-01-26 02:16:50	1
2141	邮件记录		2	0	2130	mail-log	fa:edit	system/mail/log/index	SystemMailLog	0	t	t	t		2023-01-26 02:16:50	1	2024-02-29 08:48:51	1
2301	回调通知		2	5	1117	notify	ep:mute-notification	pay/notify/index	PayNotify	0	t	t	t		2023-07-20 04:41:32	1	2024-01-18 23:56:48	1
2285	积分签到规则删除	point:sign-in-config:delete	3	4	2281				\N	0	t	t	t		2023-06-10 03:26:12		2023-06-10 03:26:12	1
2284	积分签到规则更新	point:sign-in-config:update	3	3	2281				\N	0	t	t	t		2023-06-10 03:26:12		2023-06-10 03:26:12	1
2283	积分签到规则创建	point:sign-in-config:create	3	2	2281				\N	0	t	t	t		2023-06-10 03:26:12		2023-06-10 03:26:12	1
2282	积分签到规则查询	point:sign-in-config:query	3	1	2281				\N	0	t	t	t		2023-06-10 03:26:12		2023-06-10 03:26:12	1
2281	签到配置		2	2	2300	config	ep:calendar	member/signin/config/index	SignInConfig	0	t	t	t		2023-06-10 03:26:12	1	2023-08-20 19:25:51	1
2294	用户签到积分查询	point:sign-in-record:query	3	1	2293				\N	0	t	t	t		2023-06-10 04:48:22		2023-06-10 04:48:22	1
2297	用户签到积分删除	point:sign-in-record:delete	3	4	2293				\N	0	t	t	t		2023-06-10 04:48:22		2023-06-10 04:48:22	1
2293	签到记录		2	3	2300	record	ep:chicken	member/signin/record/index	SignInRecord	0	t	t	t		2023-06-10 04:48:22	1	2023-08-20 19:26:02	1
2300	会员签到		1	11	2262	signin	ep:alarm-clock			0	t	t	t	1	2023-06-27 22:49:53	1	2023-08-20 09:23:48	1
2288	用户积分记录查询	point:record:query	3	1	2287				\N	0	t	t	t		2023-06-10 04:18:50		2023-06-10 04:18:50	1
2287	会员积分		2	10	2262	record	fa:asterisk	member/point/record/index	PointRecord	0	t	t	t		2023-06-10 04:18:50	1	2023-10-01 23:42:11	1
2331	用户分组查询	member:group:query	3	1	2330				\N	0	t	t	t		2023-08-22 13:50:06		2023-08-22 13:50:06	1
2332	用户分组创建	member:group:create	3	2	2330				\N	0	t	t	t		2023-08-22 13:50:06		2023-08-22 13:50:06	1
2333	用户分组更新	member:group:update	3	3	2330				\N	0	t	t	t		2023-08-22 13:50:06		2023-08-22 13:50:06	1
2334	用户分组删除	member:group:delete	3	4	2330				\N	0	t	t	t		2023-08-22 13:50:06		2023-08-22 13:50:06	1
2330	会员分组		2	3	2262	group	fa:group	member/group/index	MemberGroup	0	t	t	t		2023-08-22 13:50:06	1	2023-10-01 23:42:01	1
2327	会员等级创建	member:level:create	3	2	2325				\N	0	t	t	t		2023-08-22 12:41:02		2023-08-22 12:41:02	1
2328	会员等级更新	member:level:update	3	3	2325				\N	0	t	t	t		2023-08-22 12:41:02		2023-08-22 12:41:02	1
2329	会员等级删除	member:level:delete	3	4	2325				\N	0	t	t	t		2023-08-22 12:41:02		2023-08-22 12:41:02	1
2325	会员等级		2	2	2262	level	fa:level-up	member/level/index	MemberLevel	0	t	t	t		2023-08-22 12:41:01	1	2023-08-22 21:47:00	1
2321	会员标签查询	member:tag:query	3	1	2320				\N	0	t	t	t		2023-08-20 01:03:08		2023-08-20 01:03:08	1
2322	会员标签创建	member:tag:create	3	2	2320				\N	0	t	t	t		2023-08-20 01:03:08		2023-08-20 01:03:08	1
2323	会员标签更新	member:tag:update	3	3	2320				\N	0	t	t	t		2023-08-20 01:03:08		2023-08-20 01:03:08	1
2324	会员标签删除	member:tag:delete	3	4	2320				\N	0	t	t	t		2023-08-20 01:03:08		2023-08-20 01:03:08	1
2320	会员标签		2	1	2262	tag	ep:collection-tag	member/tag/index	MemberTag	0	t	t	t		2023-08-20 01:03:08	1	2023-08-20 09:23:19	1
2318	会员用户查询	member:user:query	3	1	2317				\N	0	t	t	t		2023-08-19 04:12:15		2023-08-19 04:12:15	1
2319	会员用户更新	member:user:update	3	3	2317				\N	0	t	t	t		2023-08-19 04:12:15		2023-08-19 04:12:15	1
2335	用户等级修改	member:user:update-level	3	5	2317				\N	0	t	t	t		2023-08-23 16:49:05		2023-08-23 16:50:48	1
2276	会员配置查询	member:config:query	3	1	2275					0	t	t	t		2023-06-10 02:07:44	1	2024-04-24 19:48:58	1
2277	会员配置保存	member:config:save	3	2	2275					0	t	t	t		2023-06-10 02:07:44	1	2024-04-24 19:49:28	1
2275	会员配置		2	0	2262	config	fa:archive	member/config/index	MemberConfig	0	t	t	t		2023-06-10 02:07:44	1	2023-10-01 23:41:29	1
2262	会员中心		1	55	0	/member	ep:bicycle	\N	\N	0	t	t	t	1	2023-06-10 00:42:03	1	2023-08-20 09:23:56	1
2184	自提门店导出	trade:delivery:pick-up-store:export	3	5	2179				\N	0	t	t	t		2023-05-25 10:53:29		2023-05-25 10:53:29	1
2209	秒杀活动		2	3	2030	seckill	ep:place			0	t	t	t	1	2023-06-24 17:39:13	1	2023-06-24 18:55:15	1
2305	拼团活动查询	promotion:combination-activity:query	3	1	2304					0	t	t	t	1	2023-08-12 17:54:32	1	2023-11-24 11:57:40	1
2306	拼团活动创建	promotion:combination-activity:create	3	2	2304					0	t	t	t	1	2023-08-12 17:54:49	1	2023-08-12 17:54:49	1
2307	拼团活动更新	promotion:combination-activity:update	3	3	2304					0	t	t	t	1	2023-08-12 17:55:04	1	2023-08-12 17:55:04	1
2308	拼团活动删除	promotion:combination-activity:delete	3	4	2304					0	t	t	t	1	2023-08-12 17:55:23	1	2023-08-12 17:55:23	1
2309	拼团活动关闭	promotion:combination-activity:close	3	5	2304					0	t	t	t	1	2023-08-12 17:55:37	1	2023-10-06 10:51:57	1
2304	拼团商品		2	1	2303	acitivity	ep:apple	mall/promotion/combination/activity/index	PromotionCombinationActivity	0	t	t	t	1	2023-08-12 17:22:03	1	2023-08-12 17:22:29	1
2303	拼团活动		2	3	2030	combination	fa:group			0	t	t	t	1	2023-08-12 17:19:54	1	2023-08-12 17:20:05	1
2312	砍价活动查询	promotion:bargain-activity:query	3	1	2311					0	t	t	t	1	2023-08-13 00:32:30	1	2023-08-13 00:32:30	1
2313	砍价活动创建	promotion:bargain-activity:create	3	2	2311					0	t	t	t	1	2023-08-13 00:32:44	1	2023-08-13 00:32:44	1
2314	砍价活动更新	promotion:bargain-activity:update	3	3	2311					0	t	t	t	1	2023-08-13 00:32:55	1	2023-08-13 00:32:55	1
2315	砍价活动删除	promotion:bargain-activity:delete	3	4	2311					0	t	t	t	1	2023-08-13 00:34:50	1	2023-08-13 00:34:50	1
2316	砍价活动关闭	promotion:bargain-activity:close	3	5	2311					0	t	t	t	1	2023-08-13 00:35:02	1	2023-08-13 00:35:02	1
2311	砍价商品		2	1	2310	activity	ep:burger	mall/promotion/bargain/activity/index	PromotionBargainActivity	0	t	t	t	1	2023-08-13 00:28:49	1	2023-10-05 01:16:23	1
2310	砍价活动		2	4	2030	bargain	ep:box			0	t	t	t	1	2023-08-13 00:27:25	1	2023-08-13 00:27:25	1
2364	用户余额修改	member:user:update-balance	3	7	2317					0	t	t	t		2023-10-01 14:39:43	1	2023-10-01 22:42:31	1
2388	商城首页		2	1	2362	home	ep:home-filled	mall/home/index	MallHome	0	t	t	t		2023-10-16 12:10:33		2023-10-16 12:10:33	1
2337	评论查询	product:comment:query	3	1	2336					0	t	t	t	1	2023-08-26 11:04:01	1	2023-08-26 11:04:01	1
2338	添加自评	product:comment:create	3	2	2336					0	t	t	t	1	2023-08-26 11:04:23	1	2023-08-26 11:08:18	1
2339	商家回复	product:comment:update	3	3	2336					0	t	t	t	1	2023-08-26 11:04:37	1	2023-08-26 11:04:37	1
2340	显隐评论	product:comment:update	3	4	2336					0	t	t	t	1	2023-08-26 11:04:55	1	2023-08-26 11:04:55	1
2343	交易中心配置查询	trade:config:query	3	1	2342				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2344	交易中心配置保存	trade:config:save	3	2	2342				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2342	交易配置		2	0	2072	config	ep:setting	mall/trade/config/index	TradeConfig	0	t	t	t		2023-09-28 02:46:22	1	2024-02-26 20:30:53	1
2376	订单核销	trade:order:pick-up	3	10	2076					0	t	t	t	1	2023-10-14 17:11:58	1	2023-10-14 17:11:58	1
2389	核销订单		2	2	2166	pick-up-order	ep:list	mall/trade/delivery/pickUpOrder/index	PickUpOrder	0	t	t	t		2023-10-19 16:09:51		2023-10-19 16:09:51	1
2347	分销用户查询	trade:brokerage-user:query	3	1	2346				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2348	分销用户推广人查询	trade:brokerage-user:user-query	3	2	2346				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2349	分销用户推广订单查询	trade:brokerage-user:order-query	3	3	2346				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2350	分销用户修改推广资格	trade:brokerage-user:update-brokerage-enable	3	4	2346				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2351	分销用户修改推广员	trade:brokerage-user:update-bind-user	3	5	2346				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2352	分销用户清除推广员	trade:brokerage-user:clear-bind-user	3	6	2346				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2354	佣金记录查询	trade:brokerage-record:query	3	1	2353				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2356	佣金提现查询	trade:brokerage-withdraw:query	3	1	2355				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2357	佣金提现审核	trade:brokerage-withdraw:audit	3	2	2355				\N	0	t	t	t		2023-09-28 02:46:22		2023-09-28 02:46:22	1
2355	佣金提现		2	2	2345	brokerage-withdraw	fa:credit-card	mall/trade/brokerage/withdraw/index	TradeBrokerageWithdraw	0	t	t	t		2023-09-28 02:46:22	1	2024-02-26 20:33:35	1
2353	佣金记录		2	1	2345	brokerage-record	fa:money	mall/trade/brokerage/record/index	TradeBrokerageRecord	0	t	t	t		2023-09-28 02:46:22	1	2024-02-26 20:33:30	1
2346	分销用户		2	0	2345	brokerage-user	fa-solid:user-tie	mall/trade/brokerage/user/index	TradeBrokerageUser	0	t	t	t		2023-09-28 02:46:22	1	2024-02-26 20:33:23	1
2345	分销管理		1	4	2072	brokerage	fa-solid:project-diagram			0	t	t	t		2023-09-28 02:46:22	1	2023-09-28 10:58:44	1
2378	分类查询	promotion:article-category:query	3	1	2377				\N	0	t	t	t		2023-10-16 01:26:18		2023-10-16 01:26:18	1
2379	分类创建	promotion:article-category:create	3	2	2377				\N	0	t	t	t		2023-10-16 01:26:18		2023-10-16 01:26:18	1
2380	分类更新	promotion:article-category:update	3	3	2377				\N	0	t	t	t		2023-10-16 01:26:18		2023-10-16 01:26:18	1
2381	分类删除	promotion:article-category:delete	3	4	2377				\N	0	t	t	t		2023-10-16 01:26:18		2023-10-16 01:26:18	1
2383	文章管理查询	promotion:article:query	3	1	2382				\N	0	t	t	t		2023-10-16 01:26:18		2023-10-16 01:26:18	1
2384	文章管理创建	promotion:article:create	3	2	2382				\N	0	t	t	t		2023-10-16 01:26:18		2023-10-16 01:26:18	1
2385	文章管理更新	promotion:article:update	3	3	2382				\N	0	t	t	t		2023-10-16 01:26:18		2023-10-16 01:26:18	1
2386	文章管理删除	promotion:article:delete	3	4	2382				\N	0	t	t	t		2023-10-16 01:26:18		2023-10-16 01:26:18	1
2382	文章列表		2	2	2387	article	ep:connection	mall/promotion/article/index	Article	0	t	t	t		2023-10-16 01:26:18	1	2023-10-16 09:41:19	1
2377	文章分类		2	0	2387	article/category	fa:certificate	mall/promotion/article/category/index	ArticleCategory	0	t	t	t		2023-10-16 01:26:18	1	2023-10-16 09:38:26	1
2387	内容管理		1	1	2030	content	ep:collection			0	t	t	t	1	2023-10-16 09:37:31	1	2023-10-16 09:37:31	1
2341	优惠劵发送	promotion:coupon:send	3	2	2038					0	t	t	t	1	2023-09-02 00:03:14	1	2023-09-02 00:03:14	1
2365	优惠劵		1	2	2030	coupon	fa-solid:disease			0	t	t	t	1	2023-10-03 12:39:15	1	2023-10-05 00:16:07	1
2367	砍价记录查询	promotion:bargain-record:query	3	1	2366				\N	0	t	t	t		2023-10-05 02:49:06		2023-10-05 02:49:06	1
2368	助力记录查询	promotion:bargain-help:query	3	2	2366					0	t	t	t	1	2023-10-05 12:27:49	1	2023-10-05 12:27:49	1
2366	砍价记录		2	2	2310	record	ep:list	mall/promotion/bargain/record/index	PromotionBargainRecord	0	t	t	t		2023-10-05 02:49:06	1	2023-10-05 10:50:38	1
2390	优惠活动		1	99	2030	youhui	ep:aim			0	t	t	t	1	2023-10-21 19:23:49	1	2023-10-21 19:23:49	1
2375	会员统计查询	statistics:member:query	3	1	2374				\N	0	t	t	t		2023-10-11 04:39:24		2023-10-11 04:39:24	1
2374	会员统计		2	2	2358	member	ep:avatar	mall/statistics/member/index	MemberStatistics	0	t	t	t		2023-10-11 04:39:24	1	2024-02-26 20:41:46	1
2360	交易统计查询	statistics:trade:query	3	1	2359				\N	0	t	t	t		2023-09-30 03:22:40		2023-09-30 03:22:40	1
2361	交易统计导出	statistics:trade:export	3	2	2359				\N	0	t	t	t		2023-09-30 03:22:40		2023-09-30 03:22:40	1
2359	交易统计		2	4	2358	trade	fa-solid:credit-card	mall/statistics/trade/index	TradeStatistics	0	t	t	t		2023-09-30 03:22:40	1	2024-02-26 20:42:00	1
2358	统计中心		1	75	2362	statistics	ep:data-line			0	t	t	t		2023-09-30 03:22:40	1	2023-09-30 11:54:48	1
2362	商城系统		1	59	0	/mall	ep:shop			0	t	t	t	1	2023-09-30 11:52:02	1	2023-09-30 11:52:18	1
2438	装修模板创建	promotion:diy-template:create	3	2	2436				\N	0	t	t	t		2023-10-29 14:19:25		2023-10-29 14:19:25	1
2439	装修模板更新	promotion:diy-template:update	3	3	2436				\N	0	t	t	t		2023-10-29 14:19:25		2023-10-29 14:19:25	1
2440	装修模板删除	promotion:diy-template:delete	3	4	2436				\N	0	t	t	t		2023-10-29 14:19:25		2023-10-29 14:19:25	1
2441	装修模板使用	promotion:diy-template:use	3	5	2436				\N	0	t	t	t		2023-10-29 14:19:25		2023-10-29 14:19:25	1
2443	装修页面查询	promotion:diy-page:query	3	1	2442				\N	0	t	t	t		2023-10-29 14:19:25		2023-10-29 14:19:25	1
2444	装修页面创建	promotion:diy-page:create	3	2	2442				\N	0	t	t	t		2023-10-29 14:19:26		2023-10-29 14:19:26	1
2445	装修页面更新	promotion:diy-page:update	3	3	2442				\N	0	t	t	t		2023-10-29 14:19:26		2023-10-29 14:19:26	1
2436	装修模板		2	1	2435	diy-template	fa6-solid:brush	mall/promotion/diy/template/index	DiyTemplate	0	t	t	t		2023-10-29 14:19:25		2023-10-29 14:19:25	1
2435	商城装修		2	20	2030	diy-template	fa6-solid:brush	mall/promotion/diy/template/index	DiyTemplate	0	t	t	t		2023-10-29 14:19:25		2023-10-29 14:19:25	1
2405	线索查询	crm:clue:query	3	1	2404				\N	0	t	t	t		2023-10-29 11:06:29		2023-10-29 11:06:29	1
2406	线索创建	crm:clue:create	3	2	2404				\N	0	t	t	t		2023-10-29 11:06:29		2023-10-29 11:06:29	1
2407	线索更新	crm:clue:update	3	3	2404				\N	0	t	t	t		2023-10-29 11:06:29		2023-10-29 11:06:29	1
2408	线索删除	crm:clue:delete	3	4	2404				\N	0	t	t	t		2023-10-29 11:06:29		2023-10-29 11:06:29	1
2409	线索导出	crm:clue:export	3	5	2404				\N	0	t	t	t		2023-10-29 11:06:29		2023-10-29 11:06:29	1
2404	线索管理		2	8	2397	clue	fa:pagelines	crm/clue/index	CrmClue	0	t	t	t		2023-10-29 11:06:29	1	2024-02-17 17:15:41	1
2392	客户查询	crm:customer:query	3	1	2391				\N	0	t	t	t		2023-10-29 09:04:21		2023-10-29 09:04:21	1
2393	客户创建	crm:customer:create	3	2	2391				\N	0	t	t	t		2023-10-29 09:04:21		2023-10-29 09:04:21	1
2394	客户更新	crm:customer:update	3	3	2391				\N	0	t	t	t		2023-10-29 09:04:21		2023-10-29 09:04:21	1
2395	客户删除	crm:customer:delete	3	4	2391				\N	0	t	t	t		2023-10-29 09:04:21		2023-10-29 09:04:21	1
2396	客户导出	crm:customer:export	3	5	2391				\N	0	t	t	t		2023-10-29 09:04:21		2023-10-29 09:04:21	1
2391	客户管理		2	10	2397	customer	fa:address-book-o	crm/customer/index	CrmCustomer	0	t	t	t		2023-10-29 09:04:21	1	2024-02-17 17:13:32	1
2417	联系人查询	crm:contact:query	3	1	2416				\N	0	t	t	t		2023-10-29 11:14:56		2023-10-29 11:14:56	1
2418	联系人创建	crm:contact:create	3	2	2416				\N	0	t	t	t		2023-10-29 11:14:56		2023-10-29 11:14:56	1
2419	联系人更新	crm:contact:update	3	3	2416				\N	0	t	t	t		2023-10-29 11:14:56		2023-10-29 11:14:56	1
2420	联系人删除	crm:contact:delete	3	4	2416				\N	0	t	t	t		2023-10-29 11:14:56		2023-10-29 11:14:56	1
2421	联系人导出	crm:contact:export	3	5	2416				\N	0	t	t	t		2023-10-29 11:14:56		2023-10-29 11:14:56	1
2416	联系人管理		2	20	2397	contact	fa:address-book-o	crm/contact/index	CrmContact	0	t	t	t		2023-10-29 11:14:56	1	2024-02-17 17:13:49	1
2411	商机查询	crm:business:query	3	1	2410				\N	0	t	t	t		2023-10-29 11:12:35		2023-10-29 11:12:35	1
2412	商机创建	crm:business:create	3	2	2410				\N	0	t	t	t		2023-10-29 11:12:35		2023-10-29 11:12:35	1
2413	商机更新	crm:business:update	3	3	2410				\N	0	t	t	t		2023-10-29 11:12:35		2023-10-29 11:12:35	1
2414	商机删除	crm:business:delete	3	4	2410				\N	0	t	t	t		2023-10-29 11:12:35		2023-10-29 11:12:35	1
2415	商机导出	crm:business:export	3	5	2410				\N	0	t	t	t		2023-10-29 11:12:35		2023-10-29 11:12:35	1
2410	商机管理		2	40	2397	business	fa:bus	crm/business/index	CrmBusiness	0	t	t	t		2023-10-29 11:12:35	1	2024-02-17 17:14:55	1
2399	合同查询	crm:contract:query	3	1	2398				\N	0	t	t	t		2023-10-29 10:50:41		2023-10-29 10:50:41	1
2400	合同创建	crm:contract:create	3	2	2398				\N	0	t	t	t		2023-10-29 10:50:41		2023-10-29 10:50:41	1
2401	合同更新	crm:contract:update	3	3	2398				\N	0	t	t	t		2023-10-29 10:50:41		2023-10-29 10:50:41	1
2402	合同删除	crm:contract:delete	3	4	2398				\N	0	t	t	t		2023-10-29 10:50:41		2023-10-29 10:50:41	1
2403	合同导出	crm:contract:export	3	5	2398				\N	0	t	t	t		2023-10-29 10:50:41		2023-10-29 10:50:41	1
2398	合同管理		2	50	2397	contract	ep:notebook	crm/contract/index	CrmContract	0	t	t	t		2023-10-29 10:50:41	1	2024-02-17 17:15:09	1
2423	回款管理查询	crm:receivable:query	3	1	2422				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2424	回款管理创建	crm:receivable:create	3	2	2422				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2425	回款管理更新	crm:receivable:update	3	3	2422				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2426	回款管理删除	crm:receivable:delete	3	4	2422				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2427	回款管理导出	crm:receivable:export	3	5	2422				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2422	回款管理		2	60	2397	receivable	ep:money	crm/receivable/index	CrmReceivable	0	t	t	t		2023-10-29 11:18:09	1	2024-02-17 17:16:18	1
2429	回款计划查询	crm:receivable-plan:query	3	1	2428				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2430	回款计划创建	crm:receivable-plan:create	3	2	2428				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2431	回款计划更新	crm:receivable-plan:update	3	3	2428				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2432	回款计划删除	crm:receivable-plan:delete	3	4	2428				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2433	回款计划导出	crm:receivable-plan:export	3	5	2428				\N	0	t	t	t		2023-10-29 11:18:09		2023-10-29 11:18:09	1
2397	CRM 系统		1	200	0	/crm	ep:avatar			0	t	t	t	1	2023-10-29 17:08:30	1	2024-02-04 15:37:31	1
2449	三方应用查询	system:social-client:query	3	1	2448					0	t	t	t	1	2023-11-04 12:43:12	1	2023-11-04 12:43:33	0
2450	三方应用创建	system:social-client:create	3	2	2448					0	t	t	t	1	2023-11-04 12:43:58	1	2023-11-04 12:43:58	0
2451	三方应用更新	system:social-client:update	3	3	2448					0	t	t	t	1	2023-11-04 12:44:27	1	2023-11-04 12:44:27	0
2452	三方应用删除	system:social-client:delete	3	4	2448					0	t	t	t	1	2023-11-04 12:44:43	1	2023-11-04 12:44:43	0
2479	示例联系人查询	infra:demo01-contact:query	3	1	2478				\N	0	t	t	t		2023-11-15 14:42:30		2023-11-15 14:42:30	0
2480	示例联系人创建	infra:demo01-contact:create	3	2	2478				\N	0	t	t	t		2023-11-15 14:42:30		2023-11-15 14:42:30	0
2481	示例联系人更新	infra:demo01-contact:update	3	3	2478				\N	0	t	t	t		2023-11-15 14:42:30		2023-11-15 14:42:30	0
2482	示例联系人删除	infra:demo01-contact:delete	3	4	2478				\N	0	t	t	t		2023-11-15 14:42:30		2023-11-15 14:42:30	0
2483	示例联系人导出	infra:demo01-contact:export	3	5	2478				\N	0	t	t	t		2023-11-15 14:42:30		2023-11-15 14:42:30	0
2543	关联商机	crm:contact:create-business	3	10	2416					0	t	t	t	1	2024-01-02 17:28:25	1	2024-01-02 17:28:25	1
2544	取关商机	crm:contact:delete-business	3	11	2416					0	t	t	t	1	2024-01-02 17:28:43	1	2024-01-02 17:28:51	1
2527	产品查询	crm:product:query	3	1	2526					0	t	t	t	1	2023-12-05 22:47:16	1	2023-12-05 22:47:16	1
2528	产品创建	crm:product:create	3	2	2526					0	t	t	t	1	2023-12-05 22:47:41	1	2023-12-05 22:47:48	1
2529	产品更新	crm:product:update	3	3	2526					0	t	t	t	1	2023-12-05 22:48:03	1	2023-12-05 22:48:03	1
2530	产品删除	crm:product:delete	3	4	2526					0	t	t	t	1	2023-12-05 22:48:17	1	2023-12-05 22:48:17	1
2531	产品导出	crm:product:export	3	5	2526					0	t	t	t	1	2023-12-05 22:48:29	1	2023-12-05 22:48:29	1
2526	产品管理		2	80	2397	product	fa:product-hunt	crm/product/index	CrmProduct	0	t	t	t	1	2023-12-05 22:45:26	1	2024-02-20 20:36:20	1
2517	客户公海配置保存	crm:customer-pool-config:update	3	1	2516				\N	0	t	t	t		2023-11-18 13:33:31		2023-11-18 13:33:31	1
2519	客户限制配置查询	crm:customer-limit-config:query	3	1	2518				\N	0	t	t	t		2023-11-18 13:33:53		2023-11-18 13:33:53	1
2520	客户限制配置创建	crm:customer-limit-config:create	3	2	2518				\N	0	t	t	t		2023-11-18 13:33:53		2023-11-18 13:33:53	1
2521	客户限制配置更新	crm:customer-limit-config:update	3	3	2518				\N	0	t	t	t		2023-11-18 13:33:53		2023-11-18 13:33:53	1
2522	客户限制配置删除	crm:customer-limit-config:delete	3	4	2518				\N	0	t	t	t		2023-11-18 13:33:53		2023-11-18 13:33:53	1
2523	客户限制配置导出	crm:customer-limit-config:export	3	5	2518				\N	0	t	t	t		2023-11-18 13:33:53		2023-11-18 13:33:53	1
2448	三方应用		2	1	2447	social/client	ep:set-up	views/system/social/client/index.vue	SocialClient	0	t	t	t	1	2023-11-04 12:17:19	1	2024-12-26 14:32:44.337	0
2453	三方用户	system:social-user:query	2	2	2447	social/user	ep:avatar	system/social/user/index.vue	SocialUser	0	t	t	t	1	2023-11-04 14:01:05	1	2024-12-26 14:32:49.63	0
2518	客户限制配置		2	1	2524	customer-limit-config	ep:avatar	crm/customer/limitConfig/index	CrmCustomerLimitConfig	0	t	t	t		2023-11-18 13:33:53	1	2024-02-24 16:43:33	1
2533	产品分类查询	crm:product-category:query	3	1	2532					0	t	t	t	1	2023-12-06 12:53:23	1	2023-12-06 12:53:23	1
2534	产品分类创建	crm:product-category:create	3	2	2532					0	t	t	t	1	2023-12-06 12:53:41	1	2023-12-06 12:53:41	1
2535	产品分类更新	crm:product-category:update	3	3	2532					0	t	t	t	1	2023-12-06 12:53:59	1	2023-12-06 12:53:59	1
2536	产品分类删除	crm:product-category:delete	3	4	2532					0	t	t	t	1	2023-12-06 12:54:14	1	2023-12-06 12:54:14	1
2532	产品分类配置		2	3	2524	product/category	fa-solid:window-restore	crm/product/category/index	CrmProductCategory	0	t	t	t	1	2023-12-06 12:52:36	1	2023-12-06 12:52:51	1
2524	系统配置		1	999	2397	config	ep:connection			0	t	t	t	1	2023-11-18 21:58:00	1	2024-02-17 17:14:34	1
2525	WebSocket		2	5	2740	websocket	ep:connection	infra/webSocket/index	InfraWebSocket	0	t	t	t	1	2023-11-23 19:41:55	1	2024-07-09 10:57:57.792	0
2485	示例分类查询	infra:demo02-category:query	3	1	2484				\N	0	t	t	t		2023-11-16 12:18:27		2023-11-16 12:18:27	1
2486	示例分类创建	infra:demo02-category:create	3	2	2484				\N	0	t	t	t		2023-11-16 12:18:27		2023-11-16 12:18:27	1
2487	示例分类更新	infra:demo02-category:update	3	3	2484				\N	0	t	t	t		2023-11-16 12:18:27		2023-11-16 12:18:27	1
2488	示例分类删除	infra:demo02-category:delete	3	4	2484				\N	0	t	t	t		2023-11-16 12:18:27		2023-11-16 12:18:27	1
2489	示例分类导出	infra:demo02-category:export	3	5	2484				\N	0	t	t	t		2023-11-16 12:18:27		2023-11-16 12:18:27	1
2484	树表（增删改查）		2	2	1070	demo02-category	fa:tree	infra/demo/demo02/index	Demo02Category	0	t	t	t		2023-11-16 12:18:27	1	2023-11-16 20:35:01	1
2491	学生查询	infra:demo03-student:query	3	1	2490				\N	0	t	t	t		2023-11-16 12:53:37		2023-11-16 12:53:37	1
2492	学生创建	infra:demo03-student:create	3	2	2490				\N	0	t	t	t		2023-11-16 12:53:37		2023-11-16 12:53:37	1
2493	学生更新	infra:demo03-student:update	3	3	2490				\N	0	t	t	t		2023-11-16 12:53:37		2023-11-16 12:53:37	1
2494	学生删除	infra:demo03-student:delete	3	4	2490				\N	0	t	t	t		2023-11-16 12:53:37		2023-11-16 12:53:37	1
2495	学生导出	infra:demo03-student:export	3	5	2490				\N	0	t	t	t		2023-11-16 12:53:37		2023-11-16 12:53:37	1
2490	主子表（标准）		2	10	1070	demo03-normal	fa:battery-3	infra/demo/demo03/normal/index	Demo03StudentNormal	0	t	t	t		2023-11-16 12:53:37	1	2023-11-16 23:10:03	1
2497	主子表（ERP）		2	11	1070	demo03-erp	ep:calendar	infra/demo/demo03/erp/index	Demo03StudentERP	0	t	t	t		2023-11-16 15:50:59	1	2023-11-17 13:19:56	1
2472	主子表（内嵌）		2	12	1070	demo03-inner	fa:power-off	infra/demo/demo03/inner/index	Demo03StudentInner	0	t	t	t		2023-11-13 04:39:51	1	2023-11-16 23:53:46	1
2549	支付&退款案例		2	1	2161	order	fa:paypal	pay/demo/order/index		0	t	t	t	1	2024-01-18 23:45:00	1	2024-01-18 23:47:21	1
2550	转账案例		2	2	2161	transfer	fa:transgender-alt	pay/demo/transfer/index		0	t	t	t	1	2024-01-18 23:51:16	1	2024-01-18 23:51:16	1
2553	钱包充值套餐查询	pay:wallet-recharge-package:query	3	1	2552				\N	0	t	t	t		2023-12-29 02:32:54		2023-12-29 02:32:54	1
2554	钱包充值套餐创建	pay:wallet-recharge-package:create	3	2	2552				\N	0	t	t	t		2023-12-29 02:32:54		2023-12-29 02:32:54	1
2555	钱包充值套餐更新	pay:wallet-recharge-package:update	3	3	2552				\N	0	t	t	t		2023-12-29 02:32:54		2023-12-29 02:32:54	1
2556	钱包充值套餐删除	pay:wallet-recharge-package:delete	3	4	2552				\N	0	t	t	t		2023-12-29 02:32:54		2023-12-29 02:32:54	1
2552	充值套餐		2	2	2551	wallet-recharge-package	fa:leaf	pay/wallet/rechargePackage/index	WalletRechargePackage	0	t	t	t		2023-12-29 02:32:54		2023-12-29 02:32:54	1
2558	钱包余额查询	pay:wallet:query	3	1	2557				\N	0	t	t	t		2023-12-29 02:32:54		2023-12-29 02:32:54	1
2557	钱包余额		2	1	2551	wallet-balance	fa:leaf	pay/wallet/balance/index	WalletBalance	0	t	t	t		2023-12-29 02:32:54		2023-12-29 02:32:54	1
2551	钱包管理		1	4	1117	wallet	ep:wallet			0	t	t	t		2023-12-29 02:32:54	1	2024-02-29 08:58:54	1
2547	订单查询	trade:order:query	3	1	2076					0	t	t	t	1	2024-01-16 08:52:00	1	2024-01-16 08:52:00	1
2548	订单更新	trade:order:update	3	2	2076					0	t	t	t	1	2024-01-16 08:52:21	1	2024-01-16 08:52:21	1
2545	商品统计		2	3	2358	product	fa:product-hunt	mall/statistics/product/index	ProductStatistics	0	t	t	t		2023-12-15 18:54:28	1	2024-02-26 20:41:52	1
2562	客户导入	crm:customer:import	3	6	2391					0	t	t	t	1	2024-02-01 13:09:00	1	2024-02-01 13:09:05	1
2546	客户公海		2	30	2397	customer/pool	fa-solid:swimming-pool	crm/customer/pool/index	CrmCustomerPool	0	t	t	t	1	2024-01-15 21:29:34	1	2024-02-17 17:14:18	1
2560	数据统计		1	200	2397	statistics	ep:data-line			0	t	t	t	1	2024-01-26 22:50:35	1	2024-02-24 20:10:07	1
2585	仓库查询	erp:warehouse:query	3	1	2584				\N	0	t	t	t		2024-02-04 17:12:09		2024-02-04 17:12:09	1
2586	仓库创建	erp:warehouse:create	3	2	2584				\N	0	t	t	t		2024-02-04 17:12:09		2024-02-04 17:12:09	1
2587	仓库更新	erp:warehouse:update	3	3	2584				\N	0	t	t	t		2024-02-04 17:12:09		2024-02-04 17:12:09	1
2588	仓库删除	erp:warehouse:delete	3	4	2584				\N	0	t	t	t		2024-02-04 17:12:09		2024-02-04 17:12:09	1
2589	仓库导出	erp:warehouse:export	3	5	2584				\N	0	t	t	t		2024-02-04 17:12:09		2024-02-04 17:12:09	1
2584	仓库信息		2	0	2583	warehouse	ep:house	erp/stock/warehouse/index	ErpWarehouse	0	t	t	t		2024-02-04 17:12:09	1	2024-02-05 01:12:53	1
2591	库存查询	erp:stock:query	3	1	2590				\N	0	t	t	t		2024-02-05 06:40:50		2024-02-05 06:40:50	1
2592	库存导出	erp:stock:export	3	5	2590				\N	0	t	t	t		2024-02-05 06:40:50		2024-02-05 06:40:50	1
2590	产品库存		2	1	2583	stock	ep:coffee	erp/stock/stock/index	ErpStock	0	t	t	t		2024-02-05 06:40:50	1	2024-02-05 14:42:44	1
2594	库存明细查询	erp:stock-record:query	3	1	2593				\N	0	t	t	t		2024-02-05 10:27:21		2024-02-05 10:27:21	1
2595	库存明细导出	erp:stock-record:export	3	5	2593				\N	0	t	t	t		2024-02-05 10:27:21		2024-02-05 10:27:21	1
2593	出入库明细		2	2	2583	record	fa-solid:blog	erp/stock/record/index	ErpStockRecord	0	t	t	t		2024-02-05 10:27:21	1	2024-02-06 17:26:11	1
2597	其它入库单查询	erp:stock-in:query	3	1	2596				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-05 16:08:56	1
2596	其它入库		2	3	2583	in	ep:zoom-in	erp/stock/in/index	ErpStockIn	0	t	t	t		2024-02-05 16:08:56	1	2024-02-07 19:06:51	1
2583	库存管理		1	30	2563	stock	fa:window-restore			0	t	t	t	1	2024-02-05 00:29:37	1	2024-02-05 00:29:37	1
2567	产品创建	erp:product:create	3	2	2565					0	t	t	t		2024-02-04 07:52:15	1	2024-02-04 17:22:12	1
2568	产品更新	erp:product:update	3	3	2565					0	t	t	t		2024-02-04 07:52:15	1	2024-02-04 17:22:16	1
2565	产品信息		2	0	2564	product	fa-solid:apple-alt	erp/product/product/index	ErpProduct	0	t	t	t		2024-02-04 07:52:15	1	2024-02-05 14:42:11	1
2572	分类查询	erp:product-category:query	3	1	2571				\N	0	t	t	t		2024-02-04 09:21:04		2024-02-04 09:21:04	1
2573	分类创建	erp:product-category:create	3	2	2571				\N	0	t	t	t		2024-02-04 09:21:04		2024-02-04 09:21:04	1
2574	分类更新	erp:product-category:update	3	3	2571				\N	0	t	t	t		2024-02-04 09:21:04		2024-02-04 09:21:04	1
2575	分类删除	erp:product-category:delete	3	4	2571				\N	0	t	t	t		2024-02-04 09:21:04		2024-02-04 09:21:04	1
2576	分类导出	erp:product-category:export	3	5	2571				\N	0	t	t	t		2024-02-04 09:21:04		2024-02-04 09:21:04	1
2571	产品分类		2	1	2564	product-category	fa:certificate	erp/product/category/index	ErpProductCategory	0	t	t	t		2024-02-04 09:21:04	1	2024-02-04 17:24:58	1
2578	单位查询	erp:product-unit:query	3	1	2577				\N	0	t	t	t		2024-02-04 11:54:08		2024-02-04 11:54:08	1
2579	单位创建	erp:product-unit:create	3	2	2577				\N	0	t	t	t		2024-02-04 11:54:08		2024-02-04 11:54:08	1
2580	单位更新	erp:product-unit:update	3	3	2577				\N	0	t	t	t		2024-02-04 11:54:08		2024-02-04 11:54:08	1
2581	单位删除	erp:product-unit:delete	3	4	2577				\N	0	t	t	t		2024-02-04 11:54:08		2024-02-04 11:54:08	1
2582	单位导出	erp:product-unit:export	3	5	2577				\N	0	t	t	t		2024-02-04 11:54:08		2024-02-04 11:54:08	1
2577	产品单位		2	2	2564	unit	ep:opportunity	erp/product/unit/index	ErpProductUnit	0	t	t	t		2024-02-04 11:54:08	1	2024-02-04 19:54:37	1
2564	产品管理		1	40	2563	product	fa:product-hunt			0	t	t	t	1	2024-02-04 15:38:43	1	2024-02-04 15:38:43	1
2563	ERP 系统		1	300	0	/erp	fa-solid:store			0	t	t	t	1	2024-02-04 15:37:25	1	2024-02-04 15:37:25	1
2605	供应商创建	erp:supplier:create	3	2	2603				\N	0	t	t	t		2024-02-06 08:21:55		2024-02-06 08:21:55	1
2606	供应商更新	erp:supplier:update	3	3	2603				\N	0	t	t	t		2024-02-06 08:21:55		2024-02-06 08:21:55	1
2607	供应商删除	erp:supplier:delete	3	4	2603				\N	0	t	t	t		2024-02-06 08:21:55		2024-02-06 08:21:55	1
2608	供应商导出	erp:supplier:export	3	5	2603				\N	0	t	t	t		2024-02-06 08:21:55		2024-02-06 08:21:55	1
2603	供应商信息		2	4	2602	supplier	fa:superpowers	erp/purchase/supplier/index	ErpSupplier	0	t	t	t		2024-02-06 08:21:55	1	2024-02-06 16:22:25	1
2602	采购管理		1	10	2563	purchase	fa:buysellads			0	t	t	t	1	2024-02-06 16:01:01	1	2024-02-06 16:01:23	1
2639	销售订单查询	erp:sale-order:query	3	1	2638				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:49	1
2640	销售订单创建	erp:sale-order:create	3	2	2638				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:52	1
2641	销售订单更新	erp:sale-order:update	3	3	2638				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:55	1
2642	销售订单删除	erp:sale-order:delete	3	4	2638				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:57	1
2643	销售订单导出	erp:sale-order:export	3	5	2638				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:59	1
2644	销售订单审批	erp:sale-order:update-status	3	6	2638				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:13:03	1
2638	销售订单		2	1	2617	order	fa:first-order	erp/sale/order/index	ErpSaleOrder	0	t	t	t		2024-02-05 16:08:56	1	2024-02-10 21:59:20	1
2619	客户查询	erp:customer:query	3	1	2618				\N	0	t	t	t		2024-02-07 07:21:45		2024-02-07 07:21:45	1
2620	客户创建	erp:customer:create	3	2	2618				\N	0	t	t	t		2024-02-07 07:21:45		2024-02-07 07:21:45	1
2621	客户更新	erp:customer:update	3	3	2618				\N	0	t	t	t		2024-02-07 07:21:45		2024-02-07 07:21:45	1
2622	客户删除	erp:customer:delete	3	4	2618				\N	0	t	t	t		2024-02-07 07:21:45		2024-02-07 07:21:45	1
2623	客户导出	erp:customer:export	3	5	2618				\N	0	t	t	t		2024-02-07 07:21:45		2024-02-07 07:21:45	1
2618	客户信息		2	4	2617	customer	ep:avatar	erp/sale/customer/index	ErpCustomer	0	t	t	t		2024-02-07 07:21:45	1	2024-02-07 15:22:25	1
2617	销售管理		1	20	2563	sale	fa:sellsy			0	t	t	t	1	2024-02-07 15:12:32	1	2024-02-07 15:12:32	1
2598	其它入库单创建	erp:stock-in:create	3	2	2596				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-05 16:08:56	1
2599	其它入库单更新	erp:stock-in:update	3	3	2596				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-05 16:08:56	1
2600	其它入库单删除	erp:stock-in:delete	3	4	2596				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-05 16:08:56	1
2601	其它入库单导出	erp:stock-in:export	3	5	2596				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-05 16:08:56	1
2609	其它入库单审批	erp:stock-in:update-status	3	6	2596				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-05 16:08:56	1
2611	其它出库单查询	erp:stock-out:query	3	1	2610				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 06:43:39	1
2613	其它出库单更新	erp:stock-out:update	3	3	2610				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 06:43:44	1
2614	其它出库单删除	erp:stock-out:delete	3	4	2610				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 06:43:56	1
2615	其它出库单导出	erp:stock-out:export	3	5	2610				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 06:43:57	1
2616	其它出库单审批	erp:stock-out:update-status	3	6	2610				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 06:43:58	1
2610	其它出库		2	4	2583	out	ep:zoom-out	erp/stock/out/index	ErpStockOut	0	t	t	t		2024-02-05 16:08:56	1	2024-02-07 19:06:55	1
2625	库存调度单查询	erp:stock-move:query	3	1	2624				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:49	1
2626	库存调度单创建	erp:stock-move:create	3	2	2624				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:52	1
2627	库存调度单更新	erp:stock-move:update	3	3	2624				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:55	1
2628	库存调度单删除	erp:stock-move:delete	3	4	2624				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:57	1
2629	库存调度单导出	erp:stock-move:export	3	5	2624				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:59	1
2630	库存调度单审批	erp:stock-move:update-status	3	6	2624				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:13:03	1
2624	库存调拨		2	5	2583	move	ep:folder-remove	erp/stock/move/index	ErpStockMove	0	t	t	t		2024-02-05 16:08:56	1	2024-02-16 18:53:55	1
2632	库存盘点单查询	erp:stock-check:query	3	1	2631				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:49	1
2633	库存盘点单创建	erp:stock-check:create	3	2	2631				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:52	1
2634	库存盘点单更新	erp:stock-check:update	3	3	2631				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:55	1
2635	库存盘点单删除	erp:stock-check:delete	3	4	2631				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:57	1
2636	库存盘点单导出	erp:stock-check:export	3	5	2631				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:59	1
2637	库存盘点单审批	erp:stock-check:update-status	3	6	2631				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:13:03	1
2631	库存盘点		2	6	2583	check	ep:circle-check-filled	erp/stock/check/index	ErpStockCheck	0	t	t	t		2024-02-05 16:08:56	1	2024-02-08 08:31:09	1
2647	结算账户查询	erp:account:query	3	1	2646				\N	0	t	t	t		2024-02-10 00:15:07		2024-02-10 00:15:07	1
2648	结算账户创建	erp:account:create	3	2	2646				\N	0	t	t	t		2024-02-10 00:15:07		2024-02-10 00:15:07	1
2649	结算账户更新	erp:account:update	3	3	2646				\N	0	t	t	t		2024-02-10 00:15:07		2024-02-10 00:15:07	1
2650	结算账户删除	erp:account:delete	3	4	2646				\N	0	t	t	t		2024-02-10 00:15:07		2024-02-10 00:15:07	1
2646	结算账户		2	10	2645	account	fa:universal-access	erp/finance/account/index	ErpAccount	0	t	t	t		2024-02-10 00:15:07	1	2024-02-14 08:24:31	1
2645	财务管理		1	50	2563	finance	ep:money			0	t	t	t	1	2024-02-10 08:05:58	1	2024-02-10 08:06:07	1
2702	ERP 首页	erp:statistics:query	2	0	2563	home	ep:home-filled	erp/home/index.vue	ErpHome	0	t	t	t	1	2024-02-18 16:49:40	1	2024-02-26 21:12:18	1
2667	采购订单查询	erp:purchase-order:query	3	1	2666				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:17	1
2668	采购订单创建	erp:purchase-order:create	3	2	2666				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:54	1
2669	采购订单更新	erp:purchase-order:update	3	3	2666				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:58	1
2670	采购订单删除	erp:purchase-order:delete	3	4	2666				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:00	1
2671	采购订单导出	erp:purchase-order:export	3	5	2666				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:05	1
2672	采购订单审批	erp:purchase-order:update-status	3	6	2666				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:08	1
2666	采购订单		2	1	2602	order	fa-solid:border-all	erp/purchase/order/index	ErpPurchaseOrder	0	t	t	t		2024-02-05 16:08:56	1	2024-02-12 08:51:49	1
2674	采购入库查询	erp:purchase-in:query	3	1	2673				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:17	1
2675	采购入库创建	erp:purchase-in:create	3	2	2673				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:54	1
2676	采购入库更新	erp:purchase-in:update	3	3	2673				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:58	1
2677	采购入库删除	erp:purchase-in:delete	3	4	2673				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:00	1
2678	采购入库导出	erp:purchase-in:export	3	5	2673				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:05	1
2679	采购入库审批	erp:purchase-in:update-status	3	6	2673				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:08	1
2673	采购入库		2	2	2602	in	fa-solid:gopuram	erp/purchase/in/index	ErpPurchaseIn	0	t	t	t		2024-02-05 16:08:56	1	2024-02-12 11:19:27	1
2681	采购退货查询	erp:purchase-return:query	3	1	2680				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:17	1
2682	采购退货创建	erp:purchase-return:create	3	2	2680				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:54	1
2683	采购退货更新	erp:purchase-return:update	3	3	2680				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:58	1
2685	采购退货导出	erp:purchase-return:export	3	5	2680				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:05	1
2686	采购退货审批	erp:purchase-return:update-status	3	6	2680				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:08	1
2680	采购退货		2	3	2602	return	ep:minus	erp/purchase/return/index	ErpPurchaseReturn	0	t	t	t		2024-02-05 16:08:56	1	2024-02-12 20:51:02	1
2653	销售出库查询	erp:sale-out:query	3	1	2652				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:49	1
2654	销售出库创建	erp:sale-out:create	3	2	2652				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:52	1
2655	销售出库更新	erp:sale-out:update	3	3	2652				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:55	1
2656	销售出库删除	erp:sale-out:delete	3	4	2652				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:57	1
2657	销售出库导出	erp:sale-out:export	3	5	2652				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:59	1
2658	销售出库审批	erp:sale-out:update-status	3	6	2652				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:13:03	1
2652	销售出库		2	2	2617	out	ep:sold-out	erp/sale/out/index	ErpSaleOut	0	t	t	t		2024-02-05 16:08:56	1	2024-02-10 22:02:07	1
2660	销售退货查询	erp:sale-return:query	3	1	2659				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:49	1
2661	销售退货创建	erp:sale-return:create	3	2	2659				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:52	1
2662	销售退货更新	erp:sale-return:update	3	3	2659				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:55	1
2663	销售退货删除	erp:sale-return:delete	3	4	2659				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:57	1
2664	销售退货导出	erp:sale-return:export	3	5	2659				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:12:59	1
2665	销售退货审批	erp:sale-return:update-status	3	6	2659				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 11:13:03	1
2659	销售退货		2	3	2617	return	fa-solid:bone	erp/sale/return/index	ErpSaleReturn	0	t	t	t		2024-02-05 16:08:56	1	2024-02-12 06:12:58	1
2688	付款单查询	erp:finance-payment:query	3	1	2687				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:17	1
2689	付款单创建	erp:finance-payment:create	3	2	2687				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:54	1
2690	付款单更新	erp:finance-payment:update	3	3	2687				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:58	1
2691	付款单删除	erp:finance-payment:delete	3	4	2687				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:00	1
2692	付款单导出	erp:finance-payment:export	3	5	2687				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:05	1
2693	付款单审批	erp:finance-payment:update-status	3	6	2687				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:08	1
2687	付款单		2	1	2645	payment	ep:caret-right	erp/finance/payment/index	ErpFinancePayment	0	t	t	t		2024-02-05 16:08:56	1	2024-02-14 08:24:23	1
2695	收款单查询	erp:finance-receipt:query	3	1	2694				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:17	1
2696	收款单创建	erp:finance-receipt:create	3	2	2694				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:54	1
2697	收款单更新	erp:finance-receipt:update	3	3	2694				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:44:58	1
2698	收款单删除	erp:finance-receipt:delete	3	4	2694				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:00	1
2699	收款单导出	erp:finance-receipt:export	3	5	2694				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:05	1
2700	收款单审批	erp:finance-receipt:update-status	3	6	2694				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:08	1
2694	收款单		2	2	2645	receipt	ep:expand	erp/finance/receipt/index	ErpFinanceReceipt	0	t	t	t		2024-02-05 16:08:56	1	2024-02-15 19:35:45	1
2651	结算账户导出	erp:account:export	3	5	2646				\N	0	t	t	t		2024-02-10 00:15:07		2024-02-10 00:15:07	1
2746	支付渠道创建	pay:channel:create	3	11	1126					0	t	t	t	1	2024-04-24 19:53:18	1	2024-04-24 19:53:18	1
2747	支付渠道更新	pay:channel:update	3	12	1126					0	t	t	t	1	2024-04-24 19:53:32	1	2024-04-24 19:53:58	1
2748	支付渠道删除	pay:channel:delete	3	13	1126					0	t	t	t	1	2024-04-24 19:54:34	1	2024-04-24 19:54:34	1
2720	发起流程		2	0	1200	create	fa-solid:grin-stars	bpm/processInstance/create/index	BpmProcessInstanceCreate	0	t	f	t	1	2024-03-19 19:46:05	1	2024-03-23 19:03:42	1
2725	流程任务的查询（管理员）	bpm:task:mananger-query	3	1	2724					0	t	t	t	1	2024-03-22 08:43:49	1	2024-03-22 08:43:49	1
2724	流程任务		2	11	1186	process-tasnk	ep:collection-tag	bpm/task/manager/index	BpmManagerTask	0	t	t	t	1	2024-03-22 08:43:22	1	2024-03-22 08:43:27	1
2722	流程实例的查询（管理员）	bpm:process-instance:manager-query	3	1	2721					0	t	t	t	1	2024-03-22 08:18:27	1	2024-03-22 08:19:05	1
2723	流程实例的取消（管理员）	bpm:process-instance:cancel-by-admin	3	2	2721					0	t	t	t	1	2024-03-22 08:19:25	1	2024-03-22 08:19:25	1
2721	流程实例		2	10	1186	process-instance/manager	fa:square	bpm/processInstance/manager/index	BpmProcessInstanceManager	0	t	t	t	1	2024-03-21 23:57:30	1	2024-03-21 23:57:30	1
2732	流程表达式查询	bpm:process-expression:query	3	1	2731				\N	0	t	t	t		2024-03-09 22:35:08		2024-03-09 22:35:08	1
2733	流程表达式创建	bpm:process-expression:create	3	2	2731				\N	0	t	t	t		2024-03-09 22:35:08		2024-03-09 22:35:08	1
2734	流程表达式更新	bpm:process-expression:update	3	3	2731				\N	0	t	t	t		2024-03-09 22:35:08		2024-03-09 22:35:08	1
2735	流程表达式删除	bpm:process-expression:delete	3	4	2731				\N	0	t	t	t		2024-03-09 22:35:08		2024-03-09 22:35:08	1
2731	流程表达式		2	6	1186	process-expression	fa:wpexplorer	bpm/processExpression/index	BpmProcessExpression	0	t	t	t		2024-03-09 22:35:08	1	2024-03-23 19:43:05	1
2727	流程监听器查询	bpm:process-listener:query	3	1	2726				\N	0	t	t	t		2024-03-09 16:05:34		2024-03-09 16:05:34	1
2728	流程监听器创建	bpm:process-listener:create	3	2	2726				\N	0	t	t	t		2024-03-09 16:05:34		2024-03-09 16:05:34	1
2729	流程监听器更新	bpm:process-listener:update	3	3	2726				\N	0	t	t	t		2024-03-09 16:05:34		2024-03-09 16:05:34	1
2730	流程监听器删除	bpm:process-listener:delete	3	4	2726				\N	0	t	t	t		2024-03-09 16:05:34		2024-03-09 16:05:34	1
2726	流程监听器		2	5	1186	process-listener	fa:assistive-listening-systems	bpm/processListener/index	BpmProcessListener	0	t	t	t		2024-03-09 16:05:34	1	2024-03-23 13:13:38	1
2715	分类查询	bpm:category:query	3	1	2714					0	t	t	t		2024-03-08 02:00:51	1	2024-03-19 14:36:25	1
2716	分类创建	bpm:category:create	3	2	2714					0	t	t	t		2024-03-08 02:00:51	1	2024-03-19 14:36:31	1
2717	分类更新	bpm:category:update	3	3	2714					0	t	t	t		2024-03-08 02:00:51	1	2024-03-19 14:36:35	1
2718	分类删除	bpm:category:delete	3	4	2714					0	t	t	t		2024-03-08 02:00:51	1	2024-03-19 14:36:41	1
2714	流程分类		2	3	1186	category	fa:object-ungroup	bpm/category/index	BpmCategory	0	t	t	t		2024-03-08 02:00:51	1	2024-03-21 23:51:18	1
2749	商品收藏查询	product:favorite:query	3	10	2014					0	t	t	t	1	2024-04-24 19:55:47	1	2024-04-24 19:55:47	1
2750	商品浏览查询	product:browse-history:query	3	20	2014					0	t	t	t	1	2024-04-24 19:57:43	1	2024-04-24 19:57:43	1
2751	售后同意	trade:after-sale:agree	3	2	2073					0	t	t	t	1	2024-04-24 19:58:40	1	2024-04-24 19:58:40	1
2752	售后不同意	trade:after-sale:disagree	3	3	2073					0	t	t	t	1	2024-04-24 19:59:03	1	2024-04-24 19:59:03	1
2753	售后确认退货	trade:after-sale:receive	3	4	2073					0	t	t	t	1	2024-04-24 20:00:07	1	2024-04-24 20:00:07	1
2743	商品统计查询	statistics:product:query	3	1	2545					0	t	t	t	1	2024-04-24 19:50:05	1	2024-04-24 19:50:05	1
2744	商品统计导出	statistics:product:export	3	2	2545					0	t	t	t	1	2024-04-24 19:50:26	1	2024-04-24 19:50:26	1
2741	领取公海客户	crm:customer:receive	3	1	2546					0	t	t	t	1	2024-04-24 19:47:45	1	2024-04-24 19:47:45	1
2742	分配公海客户	crm:customer:distribute	3	2	2546					0	t	t	t	1	2024-04-24 19:48:05	1	2024-04-24 19:48:05	1
2712	客户分析	crm:statistics-customer:query	2	0	2560	customer	ep:avatar	views/crm/statistics/customer/index.vue	CrmStatisticsCustomer	0	t	t	t	1	2024-03-09 16:43:56	1	2024-04-24 19:42:52	1
2736	员工业绩	crm:statistics-performance:query	2	3	2560	performance	ep:dish-dot	crm/statistics/performance/index	CrmStatisticsPerformance	0	t	t	t	1	2024-04-05 13:49:20	1	2024-04-24 19:42:43	1
2737	客户画像	crm:statistics-portrait:query	2	4	2560	portrait	ep:picture	crm/statistics/portrait/index	CrmStatisticsPortrait	0	t	t	t	1	2024-04-05 13:57:40	1	2024-04-24 19:42:24	1
2738	销售漏斗	crm:statistics-funnel:query	2	5	2560	funnel	ep:grape	crm/statistics/funnel/index	CrmStatisticsFunnel	0	t	t	t	1	2024-04-13 10:53:26	1	2024-04-24 19:39:33	1
2709	客户公海配置查询	crm:customer-pool-config:query	3	2	2516					0	t	t	t	1	2024-02-24 16:45:19	1	2024-02-24 16:45:28	1
2704	商机状态查询	crm:business-status:query	3	1	2703					0	t	t	t	1	2024-02-21 20:35:36	1	2024-02-21 20:36:06	1
2705	商机状态创建	crm:business-status:create	3	2	2703					0	t	t	t	1	2024-02-21 20:35:57	1	2024-02-21 20:35:57	1
2706	商机状态更新	crm:business-status:update	3	3	2703					0	t	t	t	1	2024-02-21 20:36:21	1	2024-02-21 20:36:21	1
2707	商机状态删除	crm:business-status:delete	3	4	2703					0	t	t	t	1	2024-02-21 20:36:36	1	2024-02-21 20:36:36	1
2703	商机状态配置		2	4	2524	business-status	fa-solid:charging-station	crm/business/status/index	CrmBusinessStatus	0	t	t	t	1	2024-02-21 20:15:17	1	2024-02-21 20:15:17	1
2710	合同配置更新	crm:contract-config:update	3	1	2708					0	t	t	t	1	2024-02-24 16:45:56	1	2024-02-24 16:45:56	1
2711	合同配置查询	crm:contract-config:query	3	2	2708					0	t	t	t	1	2024-02-24 16:46:16	1	2024-02-24 16:46:16	1
2708	合同配置		2	5	2524	contract-config	ep:connection	crm/contract/config/index	CrmContractConfig	0	t	t	t	1	2024-02-24 16:44:40	1	2024-02-24 16:44:48	1
2740	监控中心		1	40	2759	monitors	ep:monitor			0	t	t	t	1	2024-04-23 00:04:44	1	2024-07-09 11:20:40.036	0
1254	作者动态		1	0	0	https://www.iocoder.cn	ep:avatar	\N	\N	0	t	t	t	1	2022-04-23 01:03:15	1	2023-12-08 23:40:01	1
2159	Boot 开发文档		1	1	0	https://doc.iocoder.cn/	ep:document	\N	\N	0	t	t	t	1	2023-02-10 22:46:28	1	2023-12-02 21:32:20	1
1127	支付应用信息查询	pay:app:query	3	1	1126				\N	0	t	t	t		2021-11-10 01:13:31		2022-04-20 17:03:10	1
2559	转账订单		2	3	1117	transfer	ep:credit-card	pay/transfer/index	PayTransfer	0	t	t	t		2023-12-29 02:32:54		2023-12-29 02:32:54	1
2302	支付通知查询	pay:notify:query	3	1	2301				\N	0	t	t	t		2023-07-20 04:41:32		2023-07-20 04:41:32	1
2745	支付渠道查询	pay:channel:query	3	10	1126					0	t	t	t	1	2024-04-24 19:53:01	1	2024-04-24 19:53:01	1
2755	删除项目	report:go-view-project:delete	3	2	2153					0	t	t	t	1	2024-04-24 20:01:37	1	2024-04-24 20:01:37	1
5	OA 示例		1	40	1185	oa	fa:road	\N	\N	0	t	t	t	admin	2021-09-20 16:26:19	1	2024-02-29 12:38:13	1
2713	抄送我的	bpm:process-instance-cc:query	2	30	1200	copy	ep:copy-document	bpm/task/copy/index	BpmProcessInstanceCopy	0	t	t	t	1	2024-03-17 21:50:23	1	2024-04-24 19:55:12	1
1200	审批中心		2	20	1185	task	fa:tasks	\N	\N	0	t	t	t	1	2022-01-07 23:51:48	1	2024-03-21 00:33:15	1
1192	表单导出	bpm:form:export	3	5	1187				\N	0	t	t	t		2021-12-30 12:38:22	1	2022-04-20 17:03:10	1
2756	会员等级记录查询	member:level-record:query	3	10	2325					0	t	t	t	1	2024-04-24 20:02:32	1	2024-04-24 20:02:32	1
2757	会员经验记录查询	member:experience-record:query	3	11	2325					0	t	t	t	1	2024-04-24 20:02:51	1	2024-04-24 20:02:51	1
2363	用户积分修改	member:user:update-point	3	6	2317				\N	0	t	t	t		2023-10-01 14:39:43		2023-10-01 14:39:43	1
2317	会员管理		2	0	2262	user	ep:avatar	member/user/index	MemberUser	0	t	t	t		2023-08-19 04:12:15	1	2023-08-24 00:50:55	1
2015	商品查询	product:spu:query	3	1	2014				\N	0	t	t	t		2022-07-30 14:22:58		2022-07-30 14:22:58	1
2008	商品品牌		2	3	2000	brand	ep:chicken	mall/product/brand/index	ProductBrand	0	t	t	t		2022-07-30 13:52:44	1	2023-08-21 10:27:28	1
2336	商品评论		2	5	2000	comment	ep:comment	mall/product/comment/index	ProductComment	0	t	t	t	1	2023-08-26 11:03:00	1	2023-08-26 11:03:38	1
2076	订单列表		2	1	2072	order	ep:list	mall/trade/order/index	TradeOrder	0	t	t	t	1	2022-12-10 21:05:44	1	2023-10-01 21:42:08	1
2754	售后确认退款	trade:after-sale:refund	3	5	2073					0	t	t	t	1	2024-04-24 20:00:24	1	2024-04-24 20:00:24	1
2173	运费模版	trade:delivery:express-template:query	2	1	2165	express-template	ep:coordinate	mall/trade/delivery/expressTemplate/index	ExpressTemplate	0	t	t	t	1	2023-05-20 06:48:10	1	2023-08-30 21:03:13	1
2059	秒杀商品		2	2	2209	activity	ep:basketball	mall/promotion/seckill/activity/index	PromotionSeckillActivity	0	t	t	t		2022-11-06 22:24:49	1	2023-06-24 18:57:25	1
2369	拼团记录	promotion:combination-record:query	2	2	2303	record	ep:avatar	mall/promotion/combination/record/index.vue	PromotionCombinationRecord	0	t	t	t	1	2023-10-08 07:10:22	1	2023-10-08 07:34:11	1
2437	装修模板查询	promotion:diy-template:query	3	1	2436				\N	0	t	t	t		2023-10-29 14:19:25		2023-10-29 14:19:25	1
2446	装修页面删除	promotion:diy-page:delete	3	4	2442				\N	0	t	t	t		2023-10-29 14:19:26		2023-10-29 14:19:26	1
2442	装修页面		2	2	2435	diy-page	foundation:page-edit	mall/promotion/diy/page/index	DiyPage	0	t	t	t		2023-10-29 14:19:25		2023-10-29 14:19:25	1
2093	标签管理		2	3	2084	tag	ep:collection-tag	mp/tag/index	MpTag	0	t	t	t	1	2023-01-08 11:37:32	1	2024-02-29 12:42:29	1
2104	图文发表记录		2	10	2084	free-publish	ep:edit-pen	mp/freePublish/index	MpFreePublish	0	t	t	t	1	2023-01-13 00:30:50	1	2024-02-29 12:43:31	1
2701	待办事项		2	0	2397	backlog	fa-solid:tasks	crm/backlog/index	CrmBacklog	0	t	t	t	1	2024-02-17 17:17:11	1	2024-02-17 17:17:11	1
2428	回款计划		2	61	2397	receivable-plan	fa:money	crm/receivable/plan/index	CrmReceivablePlan	0	t	t	t		2023-10-29 11:18:09	1	2024-02-17 17:16:11	1
2561	排行榜	crm:statistics-rank:query	2	1	2560	ranking	fa:area-chart	crm/statistics/rank/index	CrmStatisticsRank	0	t	t	t	1	2024-01-26 22:52:09	1	2024-04-24 19:39:11	1
2516	客户公海配置		2	0	2524	customer-pool-config	ep:data-analysis	crm/customer/poolConfig/index	CrmCustomerPoolConfig	0	t	t	t		2023-11-18 13:33:31	1	2024-01-03 19:52:06	1
2684	采购退货删除	erp:purchase-return:delete	3	4	2680				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-12 00:45:00	1
2604	供应商查询	erp:supplier:query	3	1	2603				\N	0	t	t	t		2024-02-06 08:21:55		2024-02-06 08:21:55	1
2612	其它出库单创建	erp:stock-out:create	3	2	2610				\N	0	t	t	t		2024-02-05 16:08:56		2024-02-07 06:43:42	1
2758	代码生成		1	0	0	/d	ant-design:bulb-twotone	\N	\N	0	t	t	t	1	2024-07-09 10:53:56.385	1	2024-07-09 10:53:56.385	1
2761	设备管理（隐藏）		1	99	0	/device	ant-design:laptop-outlined	\N	\N	1	f	t	f	1	2024-07-10 17:55:24.188	1	2024-07-19 12:56:07.33	0
2764	设备创建	device:device:create	3	5	2762					0	t	t	t	1	2024-07-10 23:41:50.299	1	2024-07-10 23:41:50.299	0
2763	设备查询	device:device:query	3	0	2762					0	t	t	t	1	2024-07-10 23:40:58.748	1	2024-07-10 23:40:58.748	0
2765	设备更新	device:device:update	3	10	2762					0	t	t	t	1	2024-07-10 23:42:15.57	1	2024-07-10 23:42:15.57	0
2766	设备删除	device:device:delete	3	15	2762					0	t	t	t	1	2024-07-10 23:42:44.986	1	2024-07-10 23:42:57.946	0
2767	设备导出	device:device:export	3	20	2762					0	t	t	t	1	2024-07-10 23:43:28.084	1	2024-07-10 23:43:28.084	0
2817	OTA升级包查询	device:ota-packages:query	3	1	2816				\N	0	t	t	t		2024-07-11 05:23:18.760108		2024-07-11 05:23:18.760108	1
2828	OTA升级记录管理		2	0	2761	ota-records		device/ota_records/index	OtaRecords	0	t	t	t		2024-07-11 05:23:48.622944		2024-07-11 05:23:48.622944	1
2847	产品模型设备服务命令查询	device:product-commands:query	3	1	2846				\N	0	t	t	t		2024-07-11 05:24:31.78072		2024-07-11 05:24:31.78072	1
2845	产品导出	device:product:export	3	5	2931					0	t	t	t		2024-07-11 05:24:19.66822	1	2024-07-22 13:24:17.306	0
2844	产品删除	device:product:delete	3	4	2931					0	t	t	t		2024-07-11 05:24:19.66822	1	2024-07-22 13:24:10.781	0
2843	产品更新	device:product:update	3	3	2931					0	t	t	t		2024-07-11 05:24:19.66822	1	2024-07-22 13:24:04.251	0
2829	OTA升级记录查询	device:ota-records:query	3	1	2822				\N	0	t	t	t		2024-07-11 05:23:48.622944		2024-07-11 05:23:48.622944	1
2839	OTA升级任务导出	device:ota-tasks:export	3	5	2834				\N	0	t	t	t		2024-07-11 05:24:03.379076		2024-07-11 05:24:03.379076	1
2838	OTA升级任务删除	device:ota-tasks:delete	3	4	2834				\N	0	t	t	t		2024-07-11 05:24:03.379076		2024-07-11 05:24:03.379076	1
2837	OTA升级任务更新	device:ota-tasks:update	3	3	2834				\N	0	t	t	t		2024-07-11 05:24:03.379076		2024-07-11 05:24:03.379076	1
2836	OTA升级任务创建	device:ota-tasks:create	3	2	2834				\N	0	t	t	t		2024-07-11 05:24:03.379076		2024-07-11 05:24:03.379076	1
2827	OTA升级记录导出	device:ota-records:export	3	5	2822				\N	0	t	t	t		2024-07-11 05:23:32.228051		2024-07-11 05:23:32.228051	1
2826	OTA升级记录删除	device:ota-records:delete	3	4	2822				\N	0	t	t	t		2024-07-11 05:23:32.228051		2024-07-11 05:23:32.228051	1
2825	OTA升级记录更新	device:ota-records:update	3	3	2822				\N	0	t	t	t		2024-07-11 05:23:32.228051		2024-07-11 05:23:32.228051	1
2824	OTA升级记录创建	device:ota-records:create	3	2	2822				\N	0	t	t	t		2024-07-11 05:23:32.228051		2024-07-11 05:23:32.228051	1
2823	OTA升级记录查询	device:ota-records:query	3	1	2822				\N	0	t	t	t		2024-07-11 05:23:32.228051		2024-07-11 05:23:32.228051	1
2822	OTA记录管理		2	10	2930	ota-records	ant-design:clock-circle-twotone	device/ota_records/index	OtaRecords	0	t	t	t		2024-07-11 05:23:32.228051	1	2024-07-11 06:25:55.168	1
2833	OTA升级记录导出	device:ota-records:export	3	5	2822				\N	0	t	t	t		2024-07-11 05:23:48.622944		2024-07-11 05:23:48.622944	1
2832	OTA升级记录删除	device:ota-records:delete	3	4	2822				\N	0	t	t	t		2024-07-11 05:23:48.622944		2024-07-11 05:23:48.622944	1
2831	OTA升级记录更新	device:ota-records:update	3	3	2822				\N	0	t	t	t		2024-07-11 05:23:48.622944		2024-07-11 05:23:48.622944	1
2830	OTA升级记录创建	device:ota-records:create	3	2	2822				\N	0	t	t	t		2024-07-11 05:23:48.622944		2024-07-11 05:23:48.622944	1
2816	OTA包管理		2	0	2930	ota-packages	ant-design:apartment-outlined	device/ota_packages/index	OtaPackages	0	t	t	t		2024-07-11 05:23:18.760108	1	2024-07-11 06:25:45.253	1
2821	OTA升级包导出	device:ota-packages:export	3	5	2816				\N	0	t	t	t		2024-07-11 05:23:18.760108		2024-07-11 05:23:18.760108	1
2820	OTA升级包删除	device:ota-packages:delete	3	4	2816				\N	0	t	t	t		2024-07-11 05:23:18.760108		2024-07-11 05:23:18.760108	1
2819	OTA升级包更新	device:ota-packages:update	3	3	2816				\N	0	t	t	t		2024-07-11 05:23:18.760108		2024-07-11 05:23:18.760108	1
2818	OTA升级包创建	device:ota-packages:create	3	2	2816				\N	0	t	t	t		2024-07-11 05:23:18.760108		2024-07-11 05:23:18.760108	1
2814	设备Topic数据删除	device:device-topic:delete	3	4	2810				\N	0	t	t	t		2024-07-11 05:22:55.437016		2024-07-11 05:22:55.437016	0
2813	设备Topic数据更新	device:device-topic:update	3	3	2810				\N	0	t	t	t		2024-07-11 05:22:55.437016		2024-07-11 05:22:55.437016	0
2812	设备Topic数据创建	device:device-topic:create	3	2	2810				\N	0	t	t	t		2024-07-11 05:22:55.437016		2024-07-11 05:22:55.437016	0
2811	设备Topic数据查询	device:device-topic:query	3	1	2810				\N	0	t	t	t		2024-07-11 05:22:55.437016		2024-07-11 05:22:55.437016	0
2809	设备日志导出	device:device-log:export	3	5	2804				\N	0	t	t	t		2024-07-11 05:22:40.397952		2024-07-11 05:22:40.397952	0
2808	设备日志删除	device:device-log:delete	3	4	2804				\N	0	t	t	t		2024-07-11 05:22:40.397952		2024-07-11 05:22:40.397952	0
2807	设备日志更新	device:device-log:update	3	3	2804				\N	0	t	t	t		2024-07-11 05:22:40.397952		2024-07-11 05:22:40.397952	0
2806	设备日志创建	device:device-log:create	3	2	2804				\N	0	t	t	t		2024-07-11 05:22:40.397952		2024-07-11 05:22:40.397952	0
2805	设备日志查询	device:device-log:query	3	1	2804				\N	0	t	t	t		2024-07-11 05:22:40.397952		2024-07-11 05:22:40.397952	0
2803	设备分组导出	device:device-group:export	3	5	2798				\N	0	t	t	t		2024-07-11 05:21:49.127724		2024-07-11 05:21:49.127724	0
2802	设备分组删除	device:device-group:delete	3	4	2798				\N	0	t	t	t		2024-07-11 05:21:49.127724		2024-07-11 05:21:49.127724	0
2801	设备分组更新	device:device-group:update	3	3	2798				\N	0	t	t	t		2024-07-11 05:21:49.127724		2024-07-11 05:21:49.127724	0
2800	设备分组创建	device:device-group:create	3	2	2798				\N	0	t	t	t		2024-07-11 05:21:49.127724		2024-07-11 05:21:49.127724	0
2760	代码生成		2	21	1	code/codegen	ant-design:dribbble-outlined	infra/codegen/index	InfraCodegen	0	t	t	t	1	2024-07-09 11:31:13.541	1	2024-12-26 14:41:13.815	0
115	敏捷管理	infra:codegen:query	1	15	1	code	ep:document-copy	infra/codegen/index	InfraCodegen	0	t	t	t	admin	2021-01-05 17:03:48	1	2024-12-26 14:36:06.646	1
1070	生成案例		2	22	1	code/demo	ep:aim	infra/testDemo/index	\N	0	t	t	t		2021-02-06 12:42:49	1	2024-12-26 14:41:21.235	0
2799	设备分组查询	device:device-group:query	3	1	2798				\N	0	t	t	t		2024-07-11 05:21:49.127724		2024-07-11 05:21:49.127724	0
2835	OTA升级任务查询	device:ota-tasks:query	3	1	2834				\N	0	t	t	t		2024-07-11 05:24:03.379076		2024-07-11 05:24:03.379076	1
2841	产品查询	device:product:query	3	1	2931					0	t	t	t		2024-07-11 05:24:19.66822	1	2024-07-22 13:23:50.543	0
2842	产品创建	device:product:create	3	2	2931					0	t	t	t		2024-07-11 05:24:19.66822	1	2024-07-22 13:23:57.884	0
2892	产品模板删除	device:product-template:delete	3	4	2888				\N	0	t	t	t		2024-07-11 05:26:18.024696		2024-07-11 05:26:18.024696	0
2810	设备Topic		2	5	2761	device-topic	ant-design:api-filled	device/device_topic/index	DeviceTopic	0	f	t	f		2024-07-11 05:22:55.437016	1	2026-07-16 14:40:25.180872	0
2804	设备日志		2	10	2761	device-log	ant-design:appstore-twotone	device/device_log/index	DeviceLog	0	f	t	f		2024-07-11 05:22:40.397952	1	2026-07-16 14:40:54.148767	0
2840	产品列表		2	0	2939	product	ant-design:euro-outlined	device/product/index	Product	0	f	t	f		2024-07-11 05:24:19.66822	1	2026-07-16 14:41:11.957712	0
2891	产品模板更新	device:product-template:update	3	3	2888				\N	0	t	t	t		2024-07-11 05:26:18.024696		2024-07-11 05:26:18.024696	0
2890	产品模板创建	device:product-template:create	3	2	2888				\N	0	t	t	t		2024-07-11 05:26:18.024696		2024-07-11 05:26:18.024696	0
2889	产品模板查询	device:product-template:query	3	1	2888				\N	0	t	t	t		2024-07-11 05:26:18.024696		2024-07-11 05:26:18.024696	0
2882	物模型服务管理		2	1	2934	product-services	ant-design:account-book-twotone	device/product_services/index	ProductServices	0	t	t	t		2024-07-11 05:26:02.939744	1	2024-07-11 06:21:05.768	1
2887	产品模型服务导出	device:product-services:export	3	5	2882				\N	0	t	t	t		2024-07-11 05:26:02.939744		2024-07-11 05:26:02.939744	1
2886	产品模型服务删除	device:product-services:delete	3	4	2882				\N	0	t	t	t		2024-07-11 05:26:02.939744		2024-07-11 05:26:02.939744	1
2885	产品模型服务更新	device:product-services:update	3	3	2882				\N	0	t	t	t		2024-07-11 05:26:02.939744		2024-07-11 05:26:02.939744	1
2884	产品模型服务创建	device:product-services:create	3	2	2882				\N	0	t	t	t		2024-07-11 05:26:02.939744		2024-07-11 05:26:02.939744	1
2876	物模型属性管理		2	0	2934	product-properties	ant-design:laptop-outlined	device/product_properties/index	ProductProperties	0	t	t	t		2024-07-11 05:25:45.988057	1	2024-07-11 06:20:27.252	1
2881	产品模型属性导出	device:product-properties:export	3	5	2876				\N	0	t	t	t		2024-07-11 05:25:45.988057		2024-07-11 05:25:45.988057	1
2880	产品模型属性删除	device:product-properties:delete	3	4	2876				\N	0	t	t	t		2024-07-11 05:25:45.988057		2024-07-11 05:25:45.988057	1
2879	产品模型属性更新	device:product-properties:update	3	3	2876				\N	0	t	t	t		2024-07-11 05:25:45.988057		2024-07-11 05:25:45.988057	1
2878	产品模型属性创建	device:product-properties:create	3	2	2876				\N	0	t	t	t		2024-07-11 05:25:45.988057		2024-07-11 05:25:45.988057	1
2875	产品模型事件响应导出	device:product-event-response:export	3	5	2870				\N	0	t	t	t		2024-07-11 05:25:23.150952		2024-07-11 05:25:23.150952	1
2873	产品模型事件响应更新	device:product-event-response:update	3	3	2870				\N	0	t	t	t		2024-07-11 05:25:23.150952		2024-07-11 05:25:23.150952	1
2872	产品模型事件响应创建	device:product-event-response:create	3	2	2870				\N	0	t	t	t		2024-07-11 05:25:23.150952		2024-07-11 05:25:23.150952	1
2864	物模型事件管理		2	10	2934	product-event	ant-design:pull-request-outlined	device/product_event/index	ProductEvent	0	t	t	t		2024-07-11 05:25:10.088413	1	2024-07-11 06:21:56.265	1
2869	产品模型事件导出	device:product-event:export	3	5	2864				\N	0	t	t	t		2024-07-11 05:25:10.088413		2024-07-11 05:25:10.088413	1
2868	产品模型事件删除	device:product-event:delete	3	4	2864				\N	0	t	t	t		2024-07-11 05:25:10.088413		2024-07-11 05:25:10.088413	1
2867	产品模型事件更新	device:product-event:update	3	3	2864				\N	0	t	t	t		2024-07-11 05:25:10.088413		2024-07-11 05:25:10.088413	1
2866	产品模型事件创建	device:product-event:create	3	2	2864				\N	0	t	t	t		2024-07-11 05:25:10.088413		2024-07-11 05:25:10.088413	1
2858	物模型服务响应		2	5	2934	product-commands-response	ant-design:ant-design-outlined	device/product_commands_response/index	ProductCommandsResponse	0	t	t	t		2024-07-11 05:24:58.167705	1	2024-07-11 06:21:38.143	1
2863	产品模型设备响应服务命令属性导出	device:product-commands-response:export	3	5	2858				\N	0	t	t	t		2024-07-11 05:24:58.167705		2024-07-11 05:24:58.167705	1
2862	产品模型设备响应服务命令属性删除	device:product-commands-response:delete	3	4	2858				\N	0	t	t	t		2024-07-11 05:24:58.167705		2024-07-11 05:24:58.167705	1
2861	产品模型设备响应服务命令属性更新	device:product-commands-response:update	3	3	2858				\N	0	t	t	t		2024-07-11 05:24:58.167705		2024-07-11 05:24:58.167705	1
2860	产品模型设备响应服务命令属性创建	device:product-commands-response:create	3	2	2858				\N	0	t	t	t		2024-07-11 05:24:58.167705		2024-07-11 05:24:58.167705	1
2852	物模型服务下发		2	4	2934	product-commands-requests	ant-design:rocket-twotone	device/product_commands_requests/index	ProductCommandsRequests	0	t	t	t		2024-07-11 05:24:43.05179	1	2024-07-11 06:22:18.984	1
2857	产品模型设备下发服务命令属性导出	device:product-commands-requests:export	3	5	2852				\N	0	t	t	t		2024-07-11 05:24:43.05179		2024-07-11 05:24:43.05179	1
2856	产品模型设备下发服务命令属性删除	device:product-commands-requests:delete	3	4	2852				\N	0	t	t	t		2024-07-11 05:24:43.05179		2024-07-11 05:24:43.05179	1
2855	产品模型设备下发服务命令属性更新	device:product-commands-requests:update	3	3	2852				\N	0	t	t	t		2024-07-11 05:24:43.05179		2024-07-11 05:24:43.05179	1
2854	产品模型设备下发服务命令属性创建	device:product-commands-requests:create	3	2	2852				\N	0	t	t	t		2024-07-11 05:24:43.05179		2024-07-11 05:24:43.05179	1
2853	产品模型设备下发服务命令属性查询	device:product-commands-requests:query	3	1	2852				\N	0	t	t	t		2024-07-11 05:24:43.05179		2024-07-11 05:24:43.05179	1
2851	产品模型设备服务命令导出	device:product-commands:export	3	5	2846				\N	0	t	t	t		2024-07-11 05:24:31.78072		2024-07-11 05:24:31.78072	1
2850	产品模型设备服务命令删除	device:product-commands:delete	3	4	2846				\N	0	t	t	t		2024-07-11 05:24:31.78072		2024-07-11 05:24:31.78072	1
2871	产品模型事件响应查询	device:product-event-response:query	3	1	2870				\N	0	t	t	t		2024-07-11 05:25:23.150952		2024-07-11 05:25:23.150952	1
2859	产品模型设备响应服务命令属性查询	device:product-commands-response:query	3	1	2858				\N	0	t	t	t		2024-07-11 05:24:58.167705		2024-07-11 05:24:58.167705	1
2883	产品模型服务查询	device:product-services:query	3	1	2882				\N	0	t	t	t		2024-07-11 05:26:02.939744		2024-07-11 05:26:02.939744	1
2865	产品模型事件查询	device:product-event:query	3	1	2864				\N	0	t	t	t		2024-07-11 05:25:10.088413		2024-07-11 05:25:10.088413	1
2877	产品模型属性查询	device:product-properties:query	3	1	2876				\N	0	t	t	t		2024-07-11 05:25:45.988057		2024-07-11 05:25:45.988057	1
2849	产品模型设备服务命令更新	device:product-commands:update	3	3	2846				\N	0	t	t	t		2024-07-11 05:24:31.78072		2024-07-11 05:24:31.78072	1
2924	场景管理		2	5	2935	rule	ant-design:usb-twotone	device/rule/index	Rule	0	t	t	t		2024-07-11 05:27:46.10679	1	2024-07-11 06:24:44.218	1
2929	规则信息导出	device:rule:export	3	5	2924				\N	0	t	t	t		2024-07-11 05:27:46.10679		2024-07-11 05:27:46.10679	1
2928	规则信息删除	device:rule:delete	3	4	2924				\N	0	t	t	t		2024-07-11 05:27:46.10679		2024-07-11 05:27:46.10679	1
2927	规则信息更新	device:rule:update	3	3	2924				\N	0	t	t	t		2024-07-11 05:27:46.10679		2024-07-11 05:27:46.10679	1
2926	规则信息创建	device:rule:create	3	2	2924				\N	0	t	t	t		2024-07-11 05:27:46.10679		2024-07-11 05:27:46.10679	1
2913	告警规则列查询	device:rule-alarm-list:query	3	1	2912				\N	0	t	t	t		2024-07-11 05:27:18.775137		2024-07-11 05:27:18.775137	1
2923	规则告警导出	device:rule-alarm:export	3	5	2918				\N	0	t	t	t		2024-07-11 05:27:32.37771		2024-07-11 05:27:32.37771	1
2922	规则告警删除	device:rule-alarm:delete	3	4	2918				\N	0	t	t	t		2024-07-11 05:27:32.37771		2024-07-11 05:27:32.37771	1
2921	规则告警更新	device:rule-alarm:update	3	3	2918				\N	0	t	t	t		2024-07-11 05:27:32.37771		2024-07-11 05:27:32.37771	1
2920	规则告警创建	device:rule-alarm:create	3	2	2918				\N	0	t	t	t		2024-07-11 05:27:32.37771		2024-07-11 05:27:32.37771	1
2912	告警规则列管理		2	5	2932	rule-alarm-list	ant-design:calculator-twotone	device/rule_alarm_list/index	RuleAlarmList	0	t	t	t		2024-07-11 05:27:18.775137	1	2024-07-11 06:24:53.082	1
2917	告警规则列导出	device:rule-alarm-list:export	3	5	2912				\N	0	t	t	t		2024-07-11 05:27:18.775137		2024-07-11 05:27:18.775137	1
2916	告警规则列删除	device:rule-alarm-list:delete	3	4	2912				\N	0	t	t	t		2024-07-11 05:27:18.775137		2024-07-11 05:27:18.775137	1
2918	规则告警管理		2	0	2932	rule-alarm	ant-design:border-inner-outlined	device/rule_alarm/index	RuleAlarm	0	t	t	t		2024-07-11 05:27:32.37771	1	2024-07-11 06:15:23.24	1
2914	告警规则列创建	device:rule-alarm-list:create	3	2	2912				\N	0	t	t	t		2024-07-11 05:27:18.775137		2024-07-11 05:27:18.775137	1
2906	场景配置		2	0	2935	rule-conditions	ant-design:alert-twotone	device/rule_conditions/index	RuleConditions	0	t	t	t		2024-07-11 05:27:02.947998	1	2024-07-11 06:24:38.802	1
2911	规则条件导出	device:rule-conditions:export	3	5	2906				\N	0	t	t	t		2024-07-11 05:27:02.947998		2024-07-11 05:27:02.947998	1
2910	规则条件删除	device:rule-conditions:delete	3	4	2906				\N	0	t	t	t		2024-07-11 05:27:02.947998		2024-07-11 05:27:02.947998	1
2909	规则条件更新	device:rule-conditions:update	3	3	2906				\N	0	t	t	t		2024-07-11 05:27:02.947998		2024-07-11 05:27:02.947998	1
2908	规则条件创建	device:rule-conditions:create	3	2	2906				\N	0	t	t	t		2024-07-11 05:27:02.947998		2024-07-11 05:27:02.947998	1
2905	协议信息导出	device:protocol:export	3	5	2900				\N	0	t	t	t		2024-07-11 05:26:50.993119		2024-07-11 05:26:50.993119	0
2904	协议信息删除	device:protocol:delete	3	4	2900				\N	0	t	t	t		2024-07-11 05:26:50.993119		2024-07-11 05:26:50.993119	0
2903	协议信息更新	device:protocol:update	3	3	2900				\N	0	t	t	t		2024-07-11 05:26:50.993119		2024-07-11 05:26:50.993119	0
2902	协议信息创建	device:protocol:create	3	2	2900				\N	0	t	t	t		2024-07-11 05:26:50.993119		2024-07-11 05:26:50.993119	0
2901	协议信息查询	device:protocol:query	3	1	2900				\N	0	t	t	t		2024-07-11 05:26:50.993119		2024-07-11 05:26:50.993119	0
2899	产品分类导出	device:product-type:export	3	5	2894				\N	0	t	t	t		2024-07-11 05:26:35.478039		2024-07-11 05:26:35.478039	0
2898	产品分类删除	device:product-type:delete	3	4	2894				\N	0	t	t	t		2024-07-11 05:26:35.478039		2024-07-11 05:26:35.478039	0
2897	产品分类更新	device:product-type:update	3	3	2894				\N	0	t	t	t		2024-07-11 05:26:35.478039		2024-07-11 05:26:35.478039	0
2896	产品分类创建	device:product-type:create	3	2	2894				\N	0	t	t	t		2024-07-11 05:26:35.478039		2024-07-11 05:26:35.478039	0
2895	产品分类查询	device:product-type:query	3	1	2894				\N	0	t	t	t		2024-07-11 05:26:35.478039		2024-07-11 05:26:35.478039	0
2893	产品模板导出	device:product-template:export	3	5	2888				\N	0	t	t	t		2024-07-11 05:26:18.024696		2024-07-11 05:26:18.024696	0
2815	设备Topic数据导出	device:device-topic:export	3	5	2810				\N	0	t	t	t		2024-07-11 05:22:55.437016		2024-07-11 05:22:55.437016	0
2915	告警规则列更新	device:rule-alarm-list:update	3	3	2912				\N	0	t	t	t		2024-07-11 05:27:18.775137		2024-07-11 05:27:18.775137	1
2919	规则告警查询	device:rule-alarm:query	3	1	2918				\N	0	t	t	t		2024-07-11 05:27:32.37771		2024-07-11 05:27:32.37771	1
2848	产品模型设备服务命令创建	device:product-commands:create	3	2	2846				\N	0	t	t	t		2024-07-11 05:24:31.78072		2024-07-11 05:24:31.78072	1
2925	规则信息查询	device:rule:query	3	1	2924				\N	0	t	t	t		2024-07-11 05:27:46.10679		2024-07-11 05:27:46.10679	1
2907	规则条件查询	device:rule-conditions:query	3	1	2906				\N	0	t	t	t		2024-07-11 05:27:02.947998		2024-07-11 05:27:02.947998	1
2930	OTA管理	ota:ota:query	2	12	0	ota	ant-design:hourglass-outlined	ota/index	OTA	0	t	f	t	1	2024-07-11 05:41:27.303	1	2025-06-15 07:15:55.418	1
2932	监控告警	alarm:alarm:query	2	14	0	alarm	ant-design:alert-outlined	alarm/index	Alarm	0	t	t	t	1	2024-07-11 05:44:34.787	1	2025-08-11 15:29:08.186	1
2935	场景联动	scene:scene:query	2	7	0	scene	ant-design:experiment-outlined	scene/index	Scene	1	f	t	f	1	2024-07-11 05:55:41.805	1	2025-06-15 07:16:17.601	1
2933	规则引擎	rule:chain:query	2	11	0	rulechains	ant-design:ant-design-outlined	rulechains/index	RuleChains	0	t	f	t	1	2024-07-11 05:46:49.946	1	2025-06-15 07:16:23.939	0
2834	OTA任务管理		2	5	2930	ota-tasks	ant-design:build-filled	device/ota_tasks/index	OtaTasks	0	t	t	t		2024-07-11 05:24:03.379076	1	2024-07-11 06:25:50.125	1
2874	产品模型事件响应删除	device:product-event-response:delete	3	4	2870				\N	0	t	t	t		2024-07-11 05:25:23.150952		2024-07-11 05:25:23.150952	1
2870	物模型事件响应		2	15	2934	product-event-response	ant-design:gold-twotone	device/product_event_response/index	ProductEventResponse	0	t	t	t		2024-07-11 05:25:23.150952	1	2024-07-11 06:22:29.1	1
2942	规则链详情	rule:chain:query	2	99	0	/rulechains/index/:id?	ant-design:appstore-add-outlined	system/iframe/FrameDynamic	FrameBlank	0	f	f	f	1	2024-07-23 14:29:10.961	1	2024-08-02 22:51:41.776	0
2956	项目管理	project:project:list	2	40	0	project	ant-design:book-twotone	visualis/local-list	Project	0	t	t	t	1	2024-09-20 11:28:28.697	1	2024-09-20 11:28:28.697	1
2957	数据源管理	datasource:datasource:list	2	42	0	datasource	ant-design:linkedin-outlined	visualis/datasource	Datasource	0	t	t	t	1	2024-09-20 11:29:21.042	1	2024-09-20 11:29:21.042	1
2946	通道列表	channel:channel:list	2	99	0	channel/:deviceIdentification	ant-design:ci-circle-outlined	video/components/Channel/index	Channel	0	f	t	f	1	2024-08-05 17:36:32.706	1	2024-08-05 18:02:21.754	0
2900	传输协议		2	20	2939	protocol	ant-design:gold-twotone	device/protocol/index	Protocol	0	f	t	f		2024-07-11 05:26:50.993119	1	2026-07-16 14:40:45.698722	0
2894	产品分类		2	5	2939	product-type	ant-design:calculator-twotone	device/product_type/index	ProductType	0	f	t	f		2024-07-11 05:26:35.478039	1	2026-07-16 14:41:23.110351	0
2937	设备详情	device:detail:list	2	1	0	detail/:id/:productIdentification/:deviceIdentification/:deviceType	ant-design:bulb-twotone	devices/components/Drawer/index	DeviceDetail	0	t	t	t	1	2024-07-19 11:11:12.369	1	2024-07-19 16:30:17.176	1
2939	产品管理（隐藏）		1	99	0	producthidden	ant-design:account-book-twotone	\N	\N	1	t	t	t	1	2024-07-22 13:22:32.521	1	2024-07-22 13:25:28.256	0
2846	物模型服务命令		2	3	2934	product-commands	ant-design:block-outlined	device/product_commands/index	ProductCommands	0	t	t	t		2024-07-11 05:24:31.78072	1	2024-07-11 06:22:12.27	1
2934	物模型管理		1	3	0	/thing-model	ant-design:reddit-outlined	\N	\N	0	t	t	t	1	2024-07-11 05:47:58.869	1	2024-07-11 05:56:13.593	1
2936	链式规则	rule:device:query	2	0	2933	/rule-chain	ant-design:borderless-table-outlined			0	t	t	t	1	2024-07-11 06:14:01.725	1	2024-07-11 06:24:05.034	1
2941	规则查询	rule:rule:chain:query	3	0	2933					0	t	t	t	1	2024-07-22 18:02:33.687	1	2024-07-22 18:02:42.235	0
2938	设备详情	device:detail:list	2	99	0	detail/:id/:productIdentification/:deviceIdentification/:deviceType	ant-design:account-book-twotone	devices/components/Drawer/index	DeviceDetail	0	f	f	f	1	2024-07-19 18:39:55.747	1	2024-08-02 11:36:29.063	0
2958	大屏设计器	designer:designer:list	2	45	0	designer	ant-design:appstore-add-outlined	visualis/designer	Designer	0	t	t	t	1	2024-09-20 11:31:42.603	1	2024-09-20 11:31:42.603	1
2951	登出	login:login;out	2	99	0	/login	ant-design:apple-outlined	base/login/Login.vue	Login	0	t	t	t	1	2024-08-30 09:28:30.314	1	2024-08-30 09:28:30.314	1
2953	人脸识别	face:face:query	2	31	0	/face	ant-design:reddit-outlined	face/index	Face	0	t	t	t	1	2024-09-19 11:23:20.932	1	2024-09-19 13:20:17.884	1
2963	模型训练	train:train:query	2	8	0	train/index	ant-design:experiment-twotone	train/index	Train	0	t	t	t	1	2025-06-15 07:18:19.268	1	2025-06-15 07:20:00.691	1
2944	流媒体管理	video:video:query	2	0	0	/video	ant-design:video-camera-outlined	video/index	Video	0	t	t	t	1	2024-07-25 15:38:20.987	1	2025-04-29 18:34:23.289	1
2952	车辆识别	car:car:query	2	25	0	/car/index	ant-design:car-twotone	car/index	Car	1	f	f	f	1	2024-09-19 11:21:55.242	1	2024-09-29 09:57:34.424	1
1255	数据源配置	data-source-config:data-source-config:query	2	20	1	code/data-source-config	ep:data-analysis	infra/dataSourceConfig/index	InfraDataSourceConfig	0	t	t	t		2022-04-27 14:37:32	1	2024-12-26 14:41:03.089	0
2478	单表（增删改查）		2	1	1070	code/demo/demo01-contact	ep:bicycle	infra/demo/demo01/index	Demo01Contact	0	t	t	t		2023-11-15 14:42:30	1	2024-12-26 14:32:28.776	0
2447	三方登录		1	10	1	social	fa:rocket			0	t	t	t	1	2023-11-04 12:12:01	1	2024-12-26 14:36:10.667	0
2759	基础设施		1	37	0	/infra	ant-design:block-outlined	\N	\N	0	t	t	t	1	2024-07-09 11:19:56.746	1	2025-08-11 15:30:49.055	0
2739	通知管理	notice:config:query	2	17	0	/notice/index	ant-design:sound-outlined	notice/index	Notice	0	t	f	t	1	2024-04-22 23:54:30	1	2025-04-29 18:35:41.919	1
2962	模型计算	calculate:calculate:list	2	13	0	calculate/index	ant-design:crown-outlined	calculate/index	Calculate	0	t	t	t	1	2025-03-17 13:28:38.556	1	2025-06-15 07:15:37.646	1
2943	协议管理	protocol:protocol:query	2	4	0	/protocol	ant-design:trophy-outlined	protocol/index	Protocol	0	f	t	f	1	2024-07-25 15:34:33.197	1	2024-09-24 15:40:28.686	1
2950	云端录像	cloud:record:list	2	99	0	cloud-record/:deviceId/:channelId	ant-design:aliwangwang-outlined	video/components/CloudRecord/index	CloudRecord	0	f	t	f	1	2024-08-21 17:01:01.904	1	2024-08-29 15:57:25.4	1
2949	设备录像	device:record:list	2	99	0	device-record/:deviceId/:channelId	ant-design:account-book-twotone	video/components/DeviceRecord/index	DeviceRecord	0	f	t	f	1	2024-08-20 17:10:25.898	1	2024-08-29 15:57:30.631	1
2948	Demo	demo:list	2	99	0	demo	ant-design:align-center-outlined	video/components/Demo/JessibucaDemo	JessibucaDemo	0	f	t	f	1	2024-08-12 18:13:05.872	1	2024-08-29 15:57:36.404	1
2947	播放器	player:player:list	2	99	0	player	ant-design:android-filled	video/components/Player/index	Player	0	f	t	f	1	2024-08-09 15:15:54.591	1	2024-08-29 15:57:43.342	1
2945	OTA测试验证	ota:verification:list	2	99	0	otaVerification/:productIdentification/:versionId/:osPkgId/:appPkgId	ant-design:node-index-outlined	ota/components/OtaVerification/index	OtaVerification	0	f	f	f	1	2024-08-01 09:50:48.21	1	2024-08-02 11:49:31.583	1
2960	抓拍空间	space:detail:list	2	99	0	space/:dirPath	ant-design:folder-open-outlined	video/components/SnapSpaceDetail/index	SpaceDetail	0	f	f	f	1	2024-12-17 13:20:01.385	1	2024-12-27 14:12:59.486	1
2954	行人识别	person:person:query	2	28	0	/person/index	ant-design:deployment-unit-outlined	person/index	Person	1	f	f	f	1	2024-09-19 11:27:30.438	1	2024-09-29 09:59:55.906	1
2964	数据集详情	dataset:detail:list	2	99	0	detail/:id	ant-design:line-chart-outlined	dataset/components/DatasetDetail/index	DatasetDetail	0	f	t	f	1	2025-08-11 15:38:39.025	1	2025-08-11 15:39:22.203	0
2967	训练详情	train:train:detail	2	99	0	TrainTaskDetail/:modelId	hugeicons:ai-brain-03	train/components/TrainTaskDetail/index	TrainTaskDetail	0	f	t	f	1	2025-08-31 18:45:27.291	1	2025-08-31 18:45:27.291	0
1243	文件管理		1	20	0	/file	ep:files	\N		1	t	t	t	1	2022-03-16 23:47:40	1	2025-08-11 15:29:22.768	1
2955	算法商城	store:store:query	2	35	0	store	ant-design:shop-twotone	store/index	Store	0	t	f	t	1	2024-09-19 11:53:03.112	1	2025-04-29 18:36:07.501	1
2961	数据标注	dataset:dataset:query	2	8	0	/dataset	gala:data	dataset/index	Dataset	0	t	t	t	1	2025-02-10 11:10:04.178	1	2025-11-20 12:43:00.112367	0
1238	文件配置查询	infra:file-config:query	3	1	1237				\N	0	t	t	t		2022-03-15 14:35:28		2022-04-20 17:03:10	1
1091	文件查询	infra:file:query	3	1	1090				\N	0	t	t	t		2021-03-12 20:16:20		2022-04-20 17:03:10	1
2969	告警事件	alert:alert:query	2	35	0	alert	ant-design:alert-outlined	alert/index	Alarm	0	t	t	t	1	2025-11-20 12:41:30.270882	1	2025-11-20 12:41:30.270882	0
2970	通知管理	notice:notice:query	2	36	0	notice	ant-design:sound-outlined	notice/index	Notice	0	t	t	t	1	2025-11-20 12:42:17.013623	1	2025-11-20 12:43:51.584973	0
2931	产品管理	product:product:query	2	6	0	product	ant-design:apartment-outlined	product/index	Product	0	t	f	t	1	2024-07-11 05:42:44.432	1	2026-06-11 10:47:23.351937	0
2966	模型管理	train:train:query	2	9	0	train/index	hugeicons:ai-brain-03	train/index	Train	0	t	t	t	1	2025-08-31 18:41:46.818	1	2025-12-06 14:51:36.602328	0
2971	通道管理	channel:channel:query	2	99	0	Channel/:deviceIdentification	ant-design:borderless-table-outlined	gb28181/components/Channel/index	Channel	0	f	t	f	1	2026-03-06 14:32:31.248172	1	2026-03-06 14:36:39.101955	0
2968	OTA升级	ota:ota:query	2	7	0	ota	ant-design:hourglass-outlined	ota/index	OtaVersion	0	t	t	t	1	2025-11-20 12:40:20.867024	1	2026-06-11 10:47:19.091584	0
2965	流媒体管理	camera:camera:query	2	3	0	camera/index	gala:video	camera/index	Camera	0	t	t	t	1	2025-08-31 18:38:53.702	1	2026-07-21 14:18:05.976289	0
2762	设备管理	device:device:query	2	4	0	device	ant-design:laptop-outlined	devices/index	Devices	0	t	f	t	1	2024-07-10 22:42:17.76	1	2026-06-11 10:47:27.631261	0
2940	产品详情	product:detail:query	2	99	0	detail/:id/:productIdentification	ant-design:appstore-add-outlined	product/components/ProductDrawer	ProductDetail	0	f	f	f	1	2024-07-22 15:46:57.693	1	2026-07-16 12:58:34.906457	0
2888	产品模板		2	10	2939	product-template	ant-design:barcode-outlined	device/product_template/index	ProductTemplate	0	f	t	f		2024-07-11 05:26:18.024696	1	2026-07-16 14:39:58.203559	0
2798	设备分组		2	2	2761	device-group	ant-design:trophy-filled	device/device_group/index	DeviceGroup	0	f	t	f		2024-07-11 05:21:49.127724	1	2026-07-16 14:40:09.50964	0
3100	可视化		1	1	0	/visualize	ant-design:fund-projection-screen-outlined	\N	\N	0	t	t	t	1	2026-07-21 10:53:12.992697	1	2026-07-21 13:25:57.709615	1
3102	项目查询	visualize:project:query	3	1	3101				\N	0	t	t	t	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:20.892831	0
3103	项目创建	visualize:project:create	3	2	3101				\N	0	t	t	t	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:20.892831	0
3104	项目更新	visualize:project:update	3	3	3101				\N	0	t	t	t	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:20.892831	0
3105	项目删除	visualize:project:delete	3	4	3101				\N	0	t	t	t	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:20.892831	0
3106	项目发布	visualize:project:publish	3	5	3101				\N	0	t	t	t	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:20.892831	0
2959	可视化大屏	view:view:list	2	49	0	/view/index	ant-design:ant-design-outlined	view/index	View	1	f	t	f	1	2024-09-27 16:36:24.602	1	2026-07-21 10:53:20.892831	0
3107	模板查询	visualize:template:query	3	10	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3108	模板创建	visualize:template:create	3	11	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3109	模板更新	visualize:template:update	3	12	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3110	模板删除	visualize:template:delete	3	13	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3111	素材查询	visualize:asset:query	3	20	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3112	素材创建	visualize:asset:create	3	21	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3113	素材更新	visualize:asset:update	3	22	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3114	素材删除	visualize:asset:delete	3	23	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3115	数据源查询	visualize:datasource:query	3	30	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3116	数据源创建	visualize:datasource:create	3	31	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3117	数据源更新	visualize:datasource:update	3	32	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3118	数据源删除	visualize:datasource:delete	3	33	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3119	部署查询	visualize:deploy:query	3	40	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3120	部署创建	visualize:deploy:create	3	41	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3121	部署更新	visualize:deploy:update	3	42	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3122	部署删除	visualize:deploy:delete	3	43	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3123	部署上线	visualize:deploy:online	3	44	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
3124	部署下线	visualize:deploy:offline	3	45	3101				\N	0	t	t	t	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0
2972	集群管理	node:node:query	2	0	0	node/index	ant-design:ungroup-outlined	node/index	ComputeNodeManage	0	t	t	t	1	2026-06-11 10:46:06.011163	1	2026-07-21 13:25:53.05001	0
3101	可视化管理	visualize:project:query	2	1	0	index	ant-design:fund-outlined	visualize/index	Visualize	0	t	t	t	1	2026-07-21 10:53:12.992697	1	2026-07-21 14:22:48.737176	0
3200	系统对接		1	47	0	/transform	ant-design:api-outlined	\N	\N	0	t	t	t	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0
3201	系统对接	transform:pipeline:query	2	1	3200	index	ant-design:api-outlined	transform/index	Transform	0	t	t	t	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0
3202	对接概览查询	transform:overview:query	3	1	3201				\N	0	t	t	t	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0
3203	目标系统查询	transform:party:query	3	2	3201				\N	0	t	t	t	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0
3204	目标系统维护	transform:party:update	3	3	3201				\N	0	t	t	t	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0
3205	推送规则查询	transform:contract:query	3	4	3201				\N	0	t	t	t	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0
3206	推送规则维护	transform:contract:update	3	5	3201				\N	0	t	t	t	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0
3207	推送记录再推	transform:outbox:replay	3	6	3201				\N	0	t	t	t	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0
3208	失败记录再推	transform:dlq:replay	3	7	3201				\N	0	t	t	t	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0
\.


--
-- Data for Name: system_notice; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_notice (id, title, content, type, status, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
1	芋道的公众	<p>新版本内容133</p>	1	0	admin	2021-01-05 17:03:48	1	2022-05-04 21:00:20	0	1
2	维护通知：2018-07-01 系统凌晨维护	<p><img src="http://test.yudao.iocoder.cn/b7cb3cf49b4b3258bf7309a09dd2f4e5.jpg" alt="" data-href="" style=""/>11112222</p>	2	1	admin	2021-01-05 17:03:48	1	2023-12-02 20:07:26	0	1
4	我是测试标题	<p>哈哈哈哈123</p>	1	0	110	2022-02-22 01:01:25	110	2022-02-22 01:01:46	0	121
\.


--
-- Data for Name: system_notify_message; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_notify_message (id, user_id, user_type, template_id, template_code, template_nickname, template_content, template_type, template_params, read_status, read_time, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
2	1	2	1	test	123	我是 1，我开始 2 了	1	{"name":"1","what":"2"}	t	2023-02-10 00:47:04	1	2023-01-28 11:44:08	1	2023-02-10 00:47:04	0	1
3	1	2	1	test	123	我是 1，我开始 2 了	1	{"name":"1","what":"2"}	t	2023-02-10 00:47:04	1	2023-01-28 11:45:04	1	2023-02-10 00:47:04	0	1
4	103	2	2	register	系统消息	你好，欢迎 哈哈 加入大家庭！	2	{"name":"哈哈"}	f	\N	1	2023-01-28 21:02:20	1	2023-01-28 21:02:20	0	1
5	1	2	1	test	123	我是 芋艿，我开始 写代码 了	1	{"name":"芋艿","what":"写代码"}	t	2023-02-10 00:47:04	1	2023-01-28 22:21:42	1	2023-02-10 00:47:04	0	1
6	1	2	1	test	123	我是 芋艿，我开始 写代码 了	1	{"name":"芋艿","what":"写代码"}	t	2023-01-29 10:52:06	1	2023-01-28 22:22:07	1	2023-01-29 10:52:06	0	1
7	1	2	1	test	123	我是 2，我开始 3 了	1	{"name":"2","what":"3"}	t	2023-01-29 10:52:06	1	2023-01-28 23:45:21	1	2023-01-29 10:52:06	0	1
8	1	2	2	register	系统消息	你好，欢迎 123 加入大家庭！	2	{"name":"123"}	t	2023-01-29 10:52:06	1	2023-01-28 23:50:21	1	2023-01-29 10:52:06	0	1
9	247	1	4	brokerage_withdraw_audit_approve	system	您在2023-09-28 08:35:46提现￥0.09元的申请已通过审核	2	{"reason":null,"createTime":"2023-09-28 08:35:46","price":"0.09"}	f	\N	1	2023-09-28 16:36:22	1	2023-09-28 16:36:22	0	1
10	247	1	4	brokerage_withdraw_audit_approve	system	您在2023-09-30 20:59:40提现￥1.00元的申请已通过审核	2	{"reason":null,"createTime":"2023-09-30 20:59:40","price":"1.00"}	f	\N	1	2023-10-03 12:11:34	1	2023-10-03 12:11:34	0	1
\.


--
-- Data for Name: system_notify_template; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_notify_template (id, name, code, nickname, content, type, params, status, remark, creator, create_time, updater, update_time, deleted) FROM stdin;
\.


--
-- Data for Name: system_oauth2_access_token; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_oauth2_access_token (id, user_id, user_type, user_info, access_token, refresh_token, client_id, scopes, expires_time, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: system_oauth2_approve; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_oauth2_approve (id, user_id, user_type, client_id, scope, approved, expires_time, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: system_oauth2_client; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_oauth2_client (id, client_id, secret, name, logo, description, status, access_token_validity_seconds, refresh_token_validity_seconds, redirect_uris, authorized_grant_types, scopes, auto_approve_scopes, authorities, resource_ids, additional_information, creator, create_time, updater, update_time, deleted) FROM stdin;
1	default	admin123	芋道源码	http://test.yudao.iocoder.cn/a5e2e244368878a366b516805a4aabf1.png	我是描述	0	1800	2592000	["https://www.iocoder.cn","https://doc.iocoder.cn"]	["password","authorization_code","implicit","refresh_token"]	["user.read","user.write"]	[]	["user.read","user.write"]	[]	{}	1	2022-05-11 21:47:12	1	2024-02-22 16:31:52	0
40	test	test2	biubiu	http://test.yudao.iocoder.cn/277a899d573723f1fcdfb57340f00379.png	啦啦啦啦	0	1800	43200	["https://www.iocoder.cn"]	["password","authorization_code","implicit"]	["user_info","projects"]	["user_info"]	[]	[]	{}	1	2022-05-12 00:28:20	1	2023-12-02 21:01:01	0
41	yudao-sso-demo-by-code	test	基于授权码模式，如何实现 SSO 单点登录？	http://test.yudao.iocoder.cn/fe4ed36596adad5120036ef61a6d0153654544d44af8dd4ad3ffe8f759933d6f.png	\N	0	1800	43200	["http://127.0.0.1:18080"]	["authorization_code","refresh_token"]	["user.read","user.write"]	[]	[]	[]	\N	1	2022-09-29 13:28:31	1	2022-09-29 13:28:31	0
42	yudao-sso-demo-by-password	test	基于密码模式，如何实现 SSO 单点登录？	http://test.yudao.iocoder.cn/604bdc695e13b3b22745be704d1f2aa8ee05c5f26f9fead6d1ca49005afbc857.jpeg	\N	0	1800	43200	["http://127.0.0.1:18080"]	["password","refresh_token"]	["user.read","user.write"]	[]	[]	[]	\N	1	2022-10-04 17:40:16	1	2025-08-13 10:13:42.237	0
\.


--
-- Data for Name: system_oauth2_code; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_oauth2_code (id, user_id, user_type, code, client_id, scopes, expires_time, redirect_uri, state, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: system_oauth2_refresh_token; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_oauth2_refresh_token (id, user_id, refresh_token, user_type, client_id, scopes, expires_time, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: system_operate_log; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_operate_log (id, trace_id, user_id, user_type, type, sub_type, biz_id, action, extra, request_method, request_url, user_ip, user_agent, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: system_post; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_post (id, code, name, sort, status, remark, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
1	ceo	董事长	1	0		admin	2021-01-06 17:03:48	1	2023-02-11 15:19:04	0	1
2	se	项目经理	2	0		admin	2021-01-05 17:03:48	1	2023-11-15 09:18:20	0	1
4	user	普通员工	4	0	111	admin	2021-01-05 17:03:48	1	2023-12-02 10:04:37	0	1
5	HR	人力资源	5	0		1	2024-03-24 20:45:40	1	2024-03-24 20:45:40	0	1
\.


--
-- Data for Name: system_role; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_role (id, name, code, sort, data_scope, data_scope_dept_ids, status, type, remark, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
1	超级管理员	super_admin	1	1		0	1	超级管理员	admin	2021-01-05 17:03:48		2022-02-22 05:08:21	0	1
2	普通角色	common	2	2		0	1	普通角色	admin	2021-01-05 17:03:48		2022-02-22 05:08:20	0	1
109	租户管理员	tenant_admin	0	1		0	1	系统自动生成	1	2022-02-22 00:56:14	1	2022-02-22 00:56:14	0	121
111	租户管理员	tenant_admin	0	1		0	1	系统自动生成	1	2022-03-07 21:37:58	1	2022-03-07 21:37:58	0	122
101	测试账号	test1	0	1	[]	0	2	我想测试		2021-01-06 13:49:35	1	2025-08-13 09:41:57.995	0	1
\.


--
-- Data for Name: system_role_menu; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
263	109	1	1	2022-02-22 00:56:14	1	2022-02-22 00:56:14	0	121
1296	110	1	110	2022-02-23 00:23:55	110	2022-02-23 00:23:55	0	121
1578	111	1	1	2022-03-07 21:37:58	1	2022-03-07 21:37:58	0	122
1642	101	1031	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1643	101	1032	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1644	101	1033	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1645	101	1034	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1646	101	1035	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1647	101	1050	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1648	101	1051	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1649	101	1052	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
476	2	1117	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
1613	101	1119	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1614	101	1120	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1612	101	1118	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1610	101	5	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1634	101	1208	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1609	101	1221	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1611	101	1222	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1633	101	1207	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1632	101	1202	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1607	101	1219	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1608	101	1220	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1631	101	1201	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1630	101	1200	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1636	101	1210	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1637	101	1211	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1638	101	1212	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1635	101	1209	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1618	101	1188	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1619	101	1189	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1620	101	1190	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1621	101	1191	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1622	101	1192	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1617	101	1187	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1624	101	1194	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1625	101	1195	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1626	101	1196	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1627	101	1197	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1628	101	1198	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1629	101	1199	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1640	101	1215	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1604	101	1216	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1605	101	1217	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1606	101	1218	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1623	101	1193	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1616	101	1186	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
1615	101	1185	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
675	2	2	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
488	2	107	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
467	2	1107	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
460	2	1100	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
455	2	1094	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
454	2	1093	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
434	2	1	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
1650	101	1053	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1651	101	1054	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1652	101	1056	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1653	101	1057	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1654	101	1058	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1655	101	1059	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1656	101	1060	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1657	101	1066	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1658	101	1067	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1659	101	1070	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1664	101	1075	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1666	101	1077	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1667	101	1078	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1668	101	1082	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1669	101	1083	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1670	101	1084	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1671	101	1085	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1672	101	1086	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1673	101	1087	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1674	101	1088	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1675	101	1089	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1681	101	1239	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	1	1
1682	101	1240	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	1	1
1683	101	1241	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	1	1
1679	101	1237	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	1	1
1687	101	106	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1688	101	110	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1689	101	111	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1690	101	112	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1691	101	113	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1694	101	116	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	0	1
1729	109	100	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1730	109	101	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1731	109	1063	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1732	109	1064	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1733	109	1001	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1734	109	1065	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1735	109	1002	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1736	109	1003	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1737	109	1004	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1738	109	1005	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1739	109	1006	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1740	109	1007	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1741	109	1008	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1742	109	1009	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1743	109	1010	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1744	109	1011	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1745	109	1012	1	2022-09-21 22:08:51	1	2022-09-21 22:08:51	0	121
1746	111	100	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1747	111	101	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1748	111	1063	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1749	111	1064	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1750	111	1001	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1751	111	1065	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1752	111	1002	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1753	111	1003	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1754	111	1004	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1755	111	1005	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1756	111	1006	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1757	111	1007	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1758	111	1008	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1759	111	1009	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1760	111	1010	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1761	111	1011	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1762	111	1012	1	2022-09-21 22:08:52	1	2022-09-21 22:08:52	0	122
1763	109	100	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1764	109	101	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1765	109	1063	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1766	109	1064	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1767	109	1001	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1768	109	1065	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1769	109	1002	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1770	109	1003	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1771	109	1004	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1772	109	1005	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1773	109	1006	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1774	109	1007	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1775	109	1008	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1776	109	1009	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1777	109	1010	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1778	109	1011	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1779	109	1012	1	2022-09-21 22:08:53	1	2022-09-21 22:08:53	0	121
1780	111	100	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1781	111	101	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1782	111	1063	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1783	111	1064	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1784	111	1001	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1785	111	1065	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1786	111	1002	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1787	111	1003	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1788	111	1004	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1789	111	1005	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1790	111	1006	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1791	111	1007	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1792	111	1008	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1793	111	1009	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1794	111	1010	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1795	111	1011	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1796	111	1012	1	2022-09-21 22:08:54	1	2022-09-21 22:08:54	0	122
1797	109	100	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1798	109	101	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1799	109	1063	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1800	109	1064	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1801	109	1001	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1685	101	1243	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	1	1
1802	109	1065	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1803	109	1002	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1804	109	1003	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1805	109	1004	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1806	109	1005	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1807	109	1006	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1808	109	1007	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1809	109	1008	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1810	109	1009	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1811	109	1010	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1812	109	1011	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1813	109	1012	1	2022-09-21 22:08:55	1	2022-09-21 22:08:55	0	121
1814	111	100	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1815	111	101	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1816	111	1063	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1817	111	1064	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1818	111	1001	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1819	111	1065	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1820	111	1002	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1821	111	1003	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1822	111	1004	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1823	111	1005	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1824	111	1006	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1825	111	1007	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1826	111	1008	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1827	111	1009	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1828	111	1010	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1829	111	1011	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1830	111	1012	1	2022-09-21 22:08:56	1	2022-09-21 22:08:56	0	122
1831	109	103	1	2022-09-21 22:43:23	1	2022-09-21 22:43:23	0	121
1832	109	1017	1	2022-09-21 22:43:23	1	2022-09-21 22:43:23	0	121
1833	109	1018	1	2022-09-21 22:43:23	1	2022-09-21 22:43:23	0	121
1834	109	1019	1	2022-09-21 22:43:23	1	2022-09-21 22:43:23	0	121
1835	109	1020	1	2022-09-21 22:43:23	1	2022-09-21 22:43:23	0	121
1836	111	103	1	2022-09-21 22:43:24	1	2022-09-21 22:43:24	0	122
1837	111	1017	1	2022-09-21 22:43:24	1	2022-09-21 22:43:24	0	122
1838	111	1018	1	2022-09-21 22:43:24	1	2022-09-21 22:43:24	0	122
1839	111	1019	1	2022-09-21 22:43:24	1	2022-09-21 22:43:24	0	122
1840	111	1020	1	2022-09-21 22:43:24	1	2022-09-21 22:43:24	0	122
1841	109	1036	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	121
1842	109	1037	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	121
1843	109	1038	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	121
1844	109	1039	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	121
1845	109	107	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	121
1846	111	1036	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	122
1847	111	1037	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	122
1848	111	1038	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	122
1849	111	1039	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	122
1850	111	107	1	2022-09-21 22:48:13	1	2022-09-21 22:48:13	0	122
2062	2	1128	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2067	2	1133	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2082	2	1162	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2083	2	1163	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2084	2	1164	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2085	2	1165	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2086	2	1166	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2081	2	1161	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2064	2	1130	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2080	2	1150	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2068	2	1134	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2069	2	1135	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2070	2	1136	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2071	2	1137	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2066	2	1132	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2063	2	1129	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2072	2	114	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2004	2	1037	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2005	2	1038	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2006	2	1039	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2054	2	1108	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2055	2	1109	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2048	2	1101	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2049	2	1102	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2050	2	1103	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2051	2	1104	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2052	2	1105	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2053	2	1106	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2044	2	1095	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2045	2	1096	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2046	2	1097	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2047	2	1098	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
1995	2	1028	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2188	101	1024	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2088	2	1174	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2089	2	1175	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2090	2	1176	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2091	2	1177	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2092	2	1178	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2087	2	1173	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2153	2	1282	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2152	2	1281	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2181	2	2015	1	2023-01-25 08:43:12	1	2023-01-25 08:43:12	1	1
2182	2	2016	1	2023-01-25 08:43:12	1	2023-01-25 08:43:12	1	1
2183	2	2017	1	2023-01-25 08:43:12	1	2023-01-25 08:43:12	1	1
2184	2	2018	1	2023-01-25 08:43:12	1	2023-01-25 08:43:12	1	1
2180	2	2014	1	2023-01-25 08:43:12	1	2023-01-25 08:43:12	1	1
2156	2	2003	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2157	2	2004	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2158	2	2005	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2159	2	2006	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2161	2	2009	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2162	2	2010	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2163	2	2011	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2164	2	2012	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2160	2	2008	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2171	2	2020	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2172	2	2021	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2173	2	2022	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2174	2	2023	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2170	2	2019	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2154	2	2000	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2177	2	2027	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2178	2	2028	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2179	2	2029	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2175	2	2025	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
2099	2	1226	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2189	101	1	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2190	101	1025	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2191	101	1026	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2192	101	1027	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2193	101	1028	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2194	101	1029	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2195	101	1030	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2200	101	1040	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2201	101	1042	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2202	101	1043	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2203	101	1045	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2204	101	1046	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2205	101	1048	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2206	101	2083	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2207	101	1063	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2208	101	1064	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2209	101	1065	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2250	101	100	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2252	101	101	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2254	101	102	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2256	101	103	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2258	101	104	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2260	101	105	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2262	101	108	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2263	101	109	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2264	101	1138	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2265	101	1139	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2266	101	1140	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2267	101	1141	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2268	101	1142	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2269	101	1143	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2270	101	1224	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2271	101	1225	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2272	101	1226	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2273	101	1227	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2274	101	1228	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2275	101	1229	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2197	101	1037	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2198	101	1038	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2199	101	1039	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2261	101	107	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2248	101	2146	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2249	101	2147	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2251	101	2148	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2253	101	2149	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2255	101	2150	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2247	101	2145	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2259	101	2152	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2257	101	2151	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2246	101	2144	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2227	101	2132	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2229	101	2133	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2230	101	2134	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2232	101	2135	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2236	101	2137	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2238	101	2138	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2240	101	2139	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2242	101	2140	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2245	101	2143	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2234	101	2136	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2244	101	2142	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2243	101	2141	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2223	101	2130	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2226	101	1108	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2228	101	1109	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2224	101	1107	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2217	101	1101	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2218	101	1102	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2219	101	1103	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2220	101	1104	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2221	101	1105	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2222	101	1106	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2216	101	1100	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2212	101	1095	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2213	101	1096	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2214	101	1097	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2215	101	1098	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2211	101	1094	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
2282	101	1261	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2283	101	1263	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2284	101	1264	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2285	101	1265	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2286	101	1266	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2287	101	1267	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2288	101	1001	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2289	101	1002	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2290	101	1003	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2291	101	1004	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2292	101	1005	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2293	101	1006	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2294	101	1007	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2295	101	1008	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2296	101	1009	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2297	101	1010	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2298	101	1011	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2299	101	1012	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2300	101	500	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2301	101	1013	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2302	101	501	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2303	101	1014	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2304	101	1015	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2305	101	1016	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2306	101	1017	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2307	101	1018	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2308	101	1019	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2309	101	1020	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2310	101	1021	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2311	101	1022	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2312	101	1023	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	0	1
2929	109	1224	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2930	109	1225	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2931	109	1226	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2932	109	1227	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2933	109	1228	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2934	109	1229	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2935	109	1138	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2936	109	1139	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2937	109	1140	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2938	109	1141	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2939	109	1142	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2940	109	1143	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	121
2941	111	1224	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2942	111	1225	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2943	111	1226	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2944	111	1227	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2945	111	1228	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2946	111	1229	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2947	111	1138	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2948	111	1139	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2949	111	1140	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2950	111	1141	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2951	111	1142	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2952	111	1143	1	2023-12-02 23:19:40	1	2023-12-02 23:19:40	0	122
2993	109	2	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
2994	109	1031	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
2995	109	1032	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
2996	109	1033	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
2997	109	1034	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
2998	109	1035	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
2999	109	1050	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3000	109	1051	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3001	109	1052	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3002	109	1053	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3003	109	1054	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3004	109	1056	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3005	109	1057	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3006	109	1058	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3007	109	1059	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3008	109	1060	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3009	109	1066	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3010	109	1067	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3011	109	1070	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3012	109	1075	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3013	109	1076	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3014	109	1077	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3015	109	1078	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3016	109	1082	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3017	109	1083	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3018	109	1084	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3019	109	1085	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3020	109	1086	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3021	109	1087	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3022	109	1088	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3023	109	1089	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3024	109	1090	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3025	109	1091	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3026	109	1092	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3027	109	106	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3028	109	110	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3029	109	111	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3030	109	112	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3031	109	113	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3032	109	114	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3033	109	115	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3034	109	116	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3035	109	2472	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3036	109	2478	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3037	109	2479	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3038	109	2480	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3039	109	2481	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3040	109	2482	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3041	109	2483	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3042	109	2484	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3043	109	2485	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3044	109	2486	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3045	109	2487	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3046	109	2488	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3047	109	2489	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3048	109	2490	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3049	109	2491	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3050	109	2492	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3051	109	2493	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3052	109	2494	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3053	109	2495	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3054	109	2497	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3055	109	1237	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3056	109	1238	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3057	109	1239	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3058	109	1240	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3059	109	1241	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3060	109	1242	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3061	109	1243	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3062	109	2525	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3063	109	1255	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3064	109	1256	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3065	109	1257	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3066	109	1258	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3067	109	1259	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3068	109	1260	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	121
3069	111	2	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3070	111	1031	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3071	111	1032	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3072	111	1033	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3073	111	1034	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3074	111	1035	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3075	111	1050	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3076	111	1051	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3077	111	1052	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3078	111	1053	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3079	111	1054	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3080	111	1056	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3081	111	1057	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3082	111	1058	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3083	111	1059	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3084	111	1060	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3085	111	1066	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3086	111	1067	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3087	111	1070	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3088	111	1075	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3089	111	1076	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3090	111	1077	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3091	111	1078	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3092	111	1082	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3093	111	1083	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3094	111	1084	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3095	111	1085	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3096	111	1086	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3097	111	1087	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3098	111	1088	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3099	111	1089	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3100	111	1090	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3101	111	1091	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3102	111	1092	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3103	111	106	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3104	111	110	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3105	111	111	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3106	111	112	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3107	111	113	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3108	111	114	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3109	111	115	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3110	111	116	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3111	111	2472	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3112	111	2478	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3113	111	2479	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3114	111	2480	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3115	111	2481	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3116	111	2482	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3117	111	2483	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3118	111	2484	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3119	111	2485	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3120	111	2486	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3121	111	2487	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3122	111	2488	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3123	111	2489	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3124	111	2490	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3125	111	2491	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3126	111	2492	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3127	111	2493	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3128	111	2494	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3129	111	2495	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3130	111	2497	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3131	111	1237	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3132	111	1238	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3133	111	1239	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3134	111	1240	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3135	111	1241	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3136	111	1242	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3137	111	1243	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3138	111	2525	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3139	111	1255	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3140	111	1256	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3141	111	1257	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3142	111	1258	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3143	111	1259	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3144	111	1260	1	2023-12-02 23:41:02	1	2023-12-02 23:41:02	0	122
3221	109	102	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	121
3222	109	1013	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	121
3223	109	1014	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	121
3224	109	1015	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	121
3225	109	1016	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	121
3226	111	102	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	122
3227	111	1013	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	122
3228	111	1014	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	122
3229	111	1015	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	122
3230	111	1016	1	2023-12-30 11:42:36	1	2023-12-30 11:42:36	0	122
4163	109	5	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4164	109	1118	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4165	109	1119	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4166	109	1120	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4167	109	2713	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4168	109	2714	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4169	109	2715	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4170	109	2716	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4171	109	2717	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4172	109	2718	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4173	109	2720	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4174	109	1185	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4175	109	2721	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4176	109	1186	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4177	109	2722	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4178	109	1187	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4179	109	2723	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4180	109	1188	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4181	109	2724	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4182	109	1189	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4183	109	2725	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4184	109	1190	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4185	109	2726	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4186	109	1191	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4187	109	2727	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4188	109	1192	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4189	109	2728	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4190	109	1193	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4191	109	2729	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4192	109	1194	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4193	109	2730	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4194	109	1195	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4195	109	2731	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4196	109	1196	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4197	109	2732	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4198	109	1197	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4199	109	2733	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4200	109	1198	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4201	109	2734	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4202	109	1199	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4203	109	2735	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4204	109	1200	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4205	109	1201	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4206	109	1202	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4207	109	1207	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4208	109	1208	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4209	109	1209	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4210	109	1210	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4211	109	1211	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4212	109	1212	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4213	109	1213	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4214	109	1215	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4215	109	1216	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4216	109	1217	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4217	109	1218	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4218	109	1219	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4219	109	1220	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4220	109	1221	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4221	109	1222	1	2024-03-30 17:53:17	1	2024-03-30 17:53:17	0	121
4222	111	5	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4223	111	1118	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4224	111	1119	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4225	111	1120	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4226	111	2713	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4227	111	2714	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4228	111	2715	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4229	111	2716	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4230	111	2717	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4231	111	2718	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4232	111	2720	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4233	111	1185	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4234	111	2721	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4235	111	1186	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4236	111	2722	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4237	111	1187	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4238	111	2723	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4239	111	1188	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4240	111	2724	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4241	111	1189	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4242	111	2725	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4243	111	1190	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4244	111	2726	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4245	111	1191	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4246	111	2727	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4247	111	1192	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4248	111	2728	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4249	111	1193	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4250	111	2729	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4251	111	1194	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4252	111	2730	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4253	111	1195	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4254	111	2731	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4255	111	1196	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4256	111	2732	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4257	111	1197	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4258	111	2733	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4259	111	1198	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4260	111	2734	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4261	111	1199	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4262	111	2735	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4263	111	1200	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4264	111	1201	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4265	111	1202	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4266	111	1207	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4267	111	1208	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4268	111	1209	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4269	111	1210	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4270	111	1211	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4271	111	1212	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4272	111	1213	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4273	111	1215	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4274	111	1216	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4275	111	1217	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4276	111	1218	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4277	111	1219	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4278	111	1220	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4279	111	1221	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
4280	111	1222	1	2024-03-30 17:53:18	1	2024-03-30 17:53:18	0	122
5778	101	2740	1	2024-04-30 09:38:37	1	2024-04-30 09:38:37	0	1
2116	2	1254	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2061	2	1127	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
480	2	1126	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
1639	101	1213	1	2022-03-19 21:45:52	1	2022-03-19 21:45:52	1	1
2155	2	2002	1	2023-01-25 08:42:58	1	2023-01-25 08:42:58	1	1
1641	101	2	1	2022-04-01 22:21:24	1	2022-04-01 22:21:24	1	1
1692	101	114	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	1	1
5779	1	1024	1	2024-07-10 23:44:06.025	1	2024-07-10 23:44:06.025	0	1
5780	1	1025	1	2024-07-10 23:44:06.1	1	2024-07-10 23:44:06.1	0	1
5781	1	1	1	2024-07-10 23:44:06.14	1	2024-07-10 23:44:06.14	0	1
5782	1	1026	1	2024-07-10 23:44:06.182	1	2024-07-10 23:44:06.182	0	1
5783	1	1027	1	2024-07-10 23:44:06.222	1	2024-07-10 23:44:06.222	0	1
5784	1	1028	1	2024-07-10 23:44:06.259	1	2024-07-10 23:44:06.259	0	1
5785	1	1029	1	2024-07-10 23:44:06.3	1	2024-07-10 23:44:06.3	0	1
5786	1	1030	1	2024-07-10 23:44:06.355	1	2024-07-10 23:44:06.355	0	1
5787	1	1031	1	2024-07-10 23:44:06.403	1	2024-07-10 23:44:06.403	0	1
5788	1	1032	1	2024-07-10 23:44:06.458	1	2024-07-10 23:44:06.458	0	1
5789	1	1033	1	2024-07-10 23:44:06.503	1	2024-07-10 23:44:06.503	0	1
5790	1	1034	1	2024-07-10 23:44:06.544	1	2024-07-10 23:44:06.544	0	1
5791	1	1035	1	2024-07-10 23:44:06.603	1	2024-07-10 23:44:06.603	0	1
5796	1	1040	1	2024-07-10 23:44:06.821	1	2024-07-10 23:44:06.821	0	1
5797	1	1042	1	2024-07-10 23:44:06.877	1	2024-07-10 23:44:06.877	0	1
5798	1	1043	1	2024-07-10 23:44:06.921	1	2024-07-10 23:44:06.921	0	1
5799	1	1045	1	2024-07-10 23:44:06.98	1	2024-07-10 23:44:06.98	0	1
5800	1	1046	1	2024-07-10 23:44:07.026	1	2024-07-10 23:44:07.026	0	1
5801	1	1048	1	2024-07-10 23:44:07.083	1	2024-07-10 23:44:07.083	0	1
5802	1	1050	1	2024-07-10 23:44:07.12	1	2024-07-10 23:44:07.12	0	1
5803	1	1051	1	2024-07-10 23:44:07.183	1	2024-07-10 23:44:07.183	0	1
5804	1	1052	1	2024-07-10 23:44:07.304	1	2024-07-10 23:44:07.304	0	1
5805	1	1053	1	2024-07-10 23:44:07.445	1	2024-07-10 23:44:07.445	0	1
5806	1	1054	1	2024-07-10 23:44:07.601	1	2024-07-10 23:44:07.601	0	1
5807	1	1056	1	2024-07-10 23:44:07.754	1	2024-07-10 23:44:07.754	0	1
5808	1	1057	1	2024-07-10 23:44:07.913	1	2024-07-10 23:44:07.913	0	1
5809	1	1058	1	2024-07-10 23:44:08.082	1	2024-07-10 23:44:08.082	0	1
5810	1	1059	1	2024-07-10 23:44:08.264	1	2024-07-10 23:44:08.264	0	1
5811	1	2083	1	2024-07-10 23:44:08.466	1	2024-07-10 23:44:08.466	0	1
5812	1	1060	1	2024-07-10 23:44:08.761	1	2024-07-10 23:44:08.761	0	1
5813	1	1063	1	2024-07-10 23:44:09.026	1	2024-07-10 23:44:09.026	0	1
5814	1	1064	1	2024-07-10 23:44:09.303	1	2024-07-10 23:44:09.303	0	1
5815	1	1065	1	2024-07-10 23:44:09.554	1	2024-07-10 23:44:09.554	0	1
5816	1	1066	1	2024-07-10 23:44:09.788	1	2024-07-10 23:44:09.788	0	1
5817	1	1067	1	2024-07-10 23:44:10.185	1	2024-07-10 23:44:10.185	0	1
5818	1	1070	1	2024-07-10 23:44:10.675	1	2024-07-10 23:44:10.675	0	1
5819	1	1075	1	2024-07-10 23:44:10.801	1	2024-07-10 23:44:10.801	0	1
5820	1	1077	1	2024-07-10 23:44:10.879	1	2024-07-10 23:44:10.879	0	1
5821	1	1078	1	2024-07-10 23:44:10.97	1	2024-07-10 23:44:10.97	0	1
5822	1	1082	1	2024-07-10 23:44:11.14	1	2024-07-10 23:44:11.14	0	1
5823	1	1083	1	2024-07-10 23:44:11.314	1	2024-07-10 23:44:11.314	0	1
5824	1	1084	1	2024-07-10 23:44:11.431	1	2024-07-10 23:44:11.431	0	1
5825	1	1085	1	2024-07-10 23:44:11.499	1	2024-07-10 23:44:11.499	0	1
5826	1	1086	1	2024-07-10 23:44:11.554	1	2024-07-10 23:44:11.554	0	1
5827	1	1087	1	2024-07-10 23:44:11.623	1	2024-07-10 23:44:11.623	0	1
5828	1	1088	1	2024-07-10 23:44:12.042	1	2024-07-10 23:44:12.042	0	1
5829	1	1089	1	2024-07-10 23:44:12.189	1	2024-07-10 23:44:12.189	0	1
5793	1	1037	1	2024-07-10 23:44:06.701	1	2024-07-10 23:44:06.701	1	1
5794	1	1038	1	2024-07-10 23:44:06.739	1	2024-07-10 23:44:06.739	1	1
5795	1	1039	1	2024-07-10 23:44:06.783	1	2024-07-10 23:44:06.783	1	1
5839	1	1100	1	2024-07-10 23:44:14.103	1	2024-07-10 23:44:14.103	1	1
5835	1	1095	1	2024-07-10 23:44:13.882	1	2024-07-10 23:44:13.882	1	1
5836	1	1096	1	2024-07-10 23:44:13.945	1	2024-07-10 23:44:13.945	1	1
5837	1	1097	1	2024-07-10 23:44:13.987	1	2024-07-10 23:44:13.987	1	1
5838	1	1098	1	2024-07-10 23:44:14.052	1	2024-07-10 23:44:14.052	1	1
5834	1	1094	1	2024-07-10 23:44:13.769	1	2024-07-10 23:44:13.769	1	1
5833	1	1093	1	2024-07-10 23:44:13.618	1	2024-07-10 23:44:13.618	1	1
5867	1	100	1	2024-07-10 23:44:15.601	1	2024-07-10 23:44:15.601	0	1
5869	1	101	1	2024-07-10 23:44:15.721	1	2024-07-10 23:44:15.721	0	1
5871	1	102	1	2024-07-10 23:44:15.841	1	2024-07-10 23:44:15.841	0	1
5873	1	103	1	2024-07-10 23:44:15.944	1	2024-07-10 23:44:15.944	0	1
5875	1	104	1	2024-07-10 23:44:16.042	1	2024-07-10 23:44:16.042	0	1
5877	1	105	1	2024-07-10 23:44:16.142	1	2024-07-10 23:44:16.142	0	1
5878	1	106	1	2024-07-10 23:44:16.195	1	2024-07-10 23:44:16.195	0	1
5880	1	108	1	2024-07-10 23:44:16.3	1	2024-07-10 23:44:16.3	0	1
5881	1	109	1	2024-07-10 23:44:16.339	1	2024-07-10 23:44:16.339	0	1
5882	1	110	1	2024-07-10 23:44:16.378	1	2024-07-10 23:44:16.378	0	1
5883	1	111	1	2024-07-10 23:44:16.42	1	2024-07-10 23:44:16.42	0	1
5884	1	112	1	2024-07-10 23:44:16.47	1	2024-07-10 23:44:16.47	0	1
5885	1	113	1	2024-07-10 23:44:16.521	1	2024-07-10 23:44:16.521	0	1
5886	1	1138	1	2024-07-10 23:44:16.561	1	2024-07-10 23:44:16.561	0	1
5887	1	1139	1	2024-07-10 23:44:16.601	1	2024-07-10 23:44:16.601	0	1
5889	1	116	1	2024-07-10 23:44:16.685	1	2024-07-10 23:44:16.685	0	1
5890	1	1140	1	2024-07-10 23:44:16.73	1	2024-07-10 23:44:16.73	0	1
5891	1	1141	1	2024-07-10 23:44:16.778	1	2024-07-10 23:44:16.778	0	1
5892	1	1142	1	2024-07-10 23:44:16.828	1	2024-07-10 23:44:16.828	0	1
5893	1	1143	1	2024-07-10 23:44:16.88	1	2024-07-10 23:44:16.88	0	1
5894	1	2447	1	2024-07-10 23:44:16.921	1	2024-07-10 23:44:16.921	0	1
5895	1	2448	1	2024-07-10 23:44:16.959	1	2024-07-10 23:44:16.959	0	1
5896	1	2449	1	2024-07-10 23:44:17.005	1	2024-07-10 23:44:17.005	0	1
5897	1	2450	1	2024-07-10 23:44:17.045	1	2024-07-10 23:44:17.045	0	1
5898	1	2451	1	2024-07-10 23:44:17.1	1	2024-07-10 23:44:17.1	0	1
5899	1	2452	1	2024-07-10 23:44:17.144	1	2024-07-10 23:44:17.144	0	1
5900	1	2453	1	2024-07-10 23:44:17.202	1	2024-07-10 23:44:17.202	0	1
5901	1	2478	1	2024-07-10 23:44:17.242	1	2024-07-10 23:44:17.242	0	1
5902	1	2479	1	2024-07-10 23:44:17.283	1	2024-07-10 23:44:17.283	0	1
5903	1	2480	1	2024-07-10 23:44:17.339	1	2024-07-10 23:44:17.339	0	1
5904	1	2481	1	2024-07-10 23:44:17.382	1	2024-07-10 23:44:17.382	0	1
5905	1	2482	1	2024-07-10 23:44:17.443	1	2024-07-10 23:44:17.443	0	1
5906	1	2483	1	2024-07-10 23:44:17.486	1	2024-07-10 23:44:17.486	0	1
5908	1	2740	1	2024-07-10 23:44:17.564	1	2024-07-10 23:44:17.564	0	1
5909	1	2759	1	2024-07-10 23:44:17.621	1	2024-07-10 23:44:17.621	0	1
5910	1	1224	1	2024-07-10 23:44:17.661	1	2024-07-10 23:44:17.661	0	1
5911	1	2760	1	2024-07-10 23:44:17.704	1	2024-07-10 23:44:17.704	0	1
5912	1	1225	1	2024-07-10 23:44:17.741	1	2024-07-10 23:44:17.741	0	1
5913	1	2761	1	2024-07-10 23:44:17.782	1	2024-07-10 23:44:17.782	0	1
5914	1	1226	1	2024-07-10 23:44:17.821	1	2024-07-10 23:44:17.821	0	1
5915	1	2762	1	2024-07-10 23:44:17.863	1	2024-07-10 23:44:17.863	0	1
5916	1	1227	1	2024-07-10 23:44:17.903	1	2024-07-10 23:44:17.903	0	1
5917	1	2763	1	2024-07-10 23:44:17.944	1	2024-07-10 23:44:17.944	0	1
5918	1	1228	1	2024-07-10 23:44:18	1	2024-07-10 23:44:18	0	1
5919	1	2764	1	2024-07-10 23:44:18.058	1	2024-07-10 23:44:18.058	0	1
5920	1	1229	1	2024-07-10 23:44:18.108	1	2024-07-10 23:44:18.108	0	1
5865	1	2146	1	2024-07-10 23:44:15.521	1	2024-07-10 23:44:15.521	1	1
5907	1	2739	1	2024-07-10 23:44:17.523	1	2024-07-10 23:44:17.523	1	1
5866	1	2147	1	2024-07-10 23:44:15.56	1	2024-07-10 23:44:15.56	1	1
5868	1	2148	1	2024-07-10 23:44:15.66	1	2024-07-10 23:44:15.66	1	1
5870	1	2149	1	2024-07-10 23:44:15.778	1	2024-07-10 23:44:15.778	1	1
5832	1	1092	1	2024-07-10 23:44:13.264	1	2024-07-10 23:44:13.264	1	1
5830	1	1090	1	2024-07-10 23:44:12.34	1	2024-07-10 23:44:12.34	1	1
5872	1	2150	1	2024-07-10 23:44:15.883	1	2024-07-10 23:44:15.883	1	1
5864	1	2145	1	2024-07-10 23:44:15.473	1	2024-07-10 23:44:15.473	1	1
5876	1	2152	1	2024-07-10 23:44:16.099	1	2024-07-10 23:44:16.099	1	1
5874	1	2151	1	2024-07-10 23:44:16	1	2024-07-10 23:44:16	1	1
5863	1	2144	1	2024-07-10 23:44:15.422	1	2024-07-10 23:44:15.422	1	1
5850	1	2132	1	2024-07-10 23:44:14.781	1	2024-07-10 23:44:14.781	1	1
5852	1	2133	1	2024-07-10 23:44:14.902	1	2024-07-10 23:44:14.902	1	1
5853	1	2134	1	2024-07-10 23:44:14.967	1	2024-07-10 23:44:14.967	1	1
5854	1	2135	1	2024-07-10 23:44:15.004	1	2024-07-10 23:44:15.004	1	1
5848	1	2131	1	2024-07-10 23:44:14.643	1	2024-07-10 23:44:14.643	1	1
5856	1	2137	1	2024-07-10 23:44:15.079	1	2024-07-10 23:44:15.079	1	1
5857	1	2138	1	2024-07-10 23:44:15.122	1	2024-07-10 23:44:15.122	1	1
5858	1	2139	1	2024-07-10 23:44:15.178	1	2024-07-10 23:44:15.178	1	1
5862	1	2143	1	2024-07-10 23:44:15.38	1	2024-07-10 23:44:15.38	1	1
5855	1	2136	1	2024-07-10 23:44:15.042	1	2024-07-10 23:44:15.042	1	1
5861	1	2142	1	2024-07-10 23:44:15.306	1	2024-07-10 23:44:15.306	1	1
5860	1	2141	1	2024-07-10 23:44:15.261	1	2024-07-10 23:44:15.261	1	1
5846	1	2130	1	2024-07-10 23:44:14.528	1	2024-07-10 23:44:14.528	1	1
5849	1	1108	1	2024-07-10 23:44:14.711	1	2024-07-10 23:44:14.711	1	1
5851	1	1109	1	2024-07-10 23:44:14.856	1	2024-07-10 23:44:14.856	1	1
5847	1	1107	1	2024-07-10 23:44:14.581	1	2024-07-10 23:44:14.581	1	1
5840	1	1101	1	2024-07-10 23:44:14.145	1	2024-07-10 23:44:14.145	1	1
5841	1	1102	1	2024-07-10 23:44:14.202	1	2024-07-10 23:44:14.202	1	1
5842	1	1103	1	2024-07-10 23:44:14.246	1	2024-07-10 23:44:14.246	1	1
5843	1	1104	1	2024-07-10 23:44:14.302	1	2024-07-10 23:44:14.302	1	1
5844	1	1105	1	2024-07-10 23:44:14.382	1	2024-07-10 23:44:14.382	1	1
5845	1	1106	1	2024-07-10 23:44:14.46	1	2024-07-10 23:44:14.46	1	1
5921	1	2765	1	2024-07-10 23:44:18.162	1	2024-07-10 23:44:18.162	0	1
5922	1	2766	1	2024-07-10 23:44:18.22	1	2024-07-10 23:44:18.22	0	1
5923	1	2767	1	2024-07-10 23:44:18.262	1	2024-07-10 23:44:18.262	0	1
5931	1	2525	1	2024-07-10 23:44:18.682	1	2024-07-10 23:44:18.682	0	1
5932	1	1255	1	2024-07-10 23:44:18.724	1	2024-07-10 23:44:18.724	0	1
5933	1	1256	1	2024-07-10 23:44:18.765	1	2024-07-10 23:44:18.765	0	1
5934	1	1001	1	2024-07-10 23:44:18.803	1	2024-07-10 23:44:18.803	0	1
5935	1	1257	1	2024-07-10 23:44:18.842	1	2024-07-10 23:44:18.842	0	1
5936	1	1002	1	2024-07-10 23:44:18.88	1	2024-07-10 23:44:18.88	0	1
5937	1	1258	1	2024-07-10 23:44:18.923	1	2024-07-10 23:44:18.923	0	1
5938	1	1003	1	2024-07-10 23:44:18.965	1	2024-07-10 23:44:18.965	0	1
5939	1	1259	1	2024-07-10 23:44:19.017	1	2024-07-10 23:44:19.017	0	1
5940	1	1004	1	2024-07-10 23:44:19.059	1	2024-07-10 23:44:19.059	0	1
5941	1	1260	1	2024-07-10 23:44:19.099	1	2024-07-10 23:44:19.099	0	1
5942	1	1005	1	2024-07-10 23:44:19.141	1	2024-07-10 23:44:19.141	0	1
5943	1	1261	1	2024-07-10 23:44:19.218	1	2024-07-10 23:44:19.218	0	1
5944	1	1006	1	2024-07-10 23:44:19.26	1	2024-07-10 23:44:19.26	0	1
5945	1	1007	1	2024-07-10 23:44:19.307	1	2024-07-10 23:44:19.307	0	1
5946	1	1263	1	2024-07-10 23:44:19.389	1	2024-07-10 23:44:19.389	0	1
5947	1	1008	1	2024-07-10 23:44:19.443	1	2024-07-10 23:44:19.443	0	1
5948	1	1264	1	2024-07-10 23:44:19.485	1	2024-07-10 23:44:19.485	0	1
5949	1	1009	1	2024-07-10 23:44:19.524	1	2024-07-10 23:44:19.524	0	1
5950	1	1265	1	2024-07-10 23:44:19.562	1	2024-07-10 23:44:19.562	0	1
5951	1	1010	1	2024-07-10 23:44:19.599	1	2024-07-10 23:44:19.599	0	1
5952	1	1266	1	2024-07-10 23:44:19.659	1	2024-07-10 23:44:19.659	0	1
5953	1	1011	1	2024-07-10 23:44:19.7	1	2024-07-10 23:44:19.7	0	1
5954	1	1267	1	2024-07-10 23:44:19.78	1	2024-07-10 23:44:19.78	0	1
5955	1	500	1	2024-07-10 23:44:19.826	1	2024-07-10 23:44:19.826	0	1
5956	1	1012	1	2024-07-10 23:44:19.881	1	2024-07-10 23:44:19.881	0	1
5957	1	501	1	2024-07-10 23:44:19.947	1	2024-07-10 23:44:19.947	0	1
5958	1	1013	1	2024-07-10 23:44:19.998	1	2024-07-10 23:44:19.998	0	1
5959	1	1014	1	2024-07-10 23:44:20.048	1	2024-07-10 23:44:20.048	0	1
5960	1	1015	1	2024-07-10 23:44:20.096	1	2024-07-10 23:44:20.096	0	1
5961	1	1016	1	2024-07-10 23:44:20.14	1	2024-07-10 23:44:20.14	0	1
5962	1	1017	1	2024-07-10 23:44:20.182	1	2024-07-10 23:44:20.182	0	1
5963	1	1018	1	2024-07-10 23:44:20.219	1	2024-07-10 23:44:20.219	0	1
5964	1	1019	1	2024-07-10 23:44:20.26	1	2024-07-10 23:44:20.26	0	1
5965	1	1020	1	2024-07-10 23:44:20.298	1	2024-07-10 23:44:20.298	0	1
5966	1	1021	1	2024-07-10 23:44:20.338	1	2024-07-10 23:44:20.338	0	1
5967	1	1022	1	2024-07-10 23:44:20.378	1	2024-07-10 23:44:20.378	0	1
5968	1	1023	1	2024-07-10 23:44:20.422	1	2024-07-10 23:44:20.422	0	1
2003	2	1036	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2196	101	1036	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
5792	1	1036	1	2024-07-10 23:44:06.657	1	2024-07-10 23:44:06.657	1	1
5879	1	107	1	2024-07-10 23:44:16.26	1	2024-07-10 23:44:16.26	1	1
2225	101	2131	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
5859	1	2140	1	2024-07-10 23:44:15.223	1	2024-07-10 23:44:15.223	1	1
5926	1	1239	1	2024-07-10 23:44:18.423	1	2024-07-10 23:44:18.423	1	1
5927	1	1240	1	2024-07-10 23:44:18.482	1	2024-07-10 23:44:18.482	1	1
5928	1	1241	1	2024-07-10 23:44:18.538	1	2024-07-10 23:44:18.538	1	1
5929	1	1242	1	2024-07-10 23:44:18.591	1	2024-07-10 23:44:18.591	1	1
5924	1	1237	1	2024-07-10 23:44:18.301	1	2024-07-10 23:44:18.301	1	1
5930	1	1243	1	2024-07-10 23:44:18.641	1	2024-07-10 23:44:18.641	1	1
2210	101	1093	1	2023-02-09 23:49:46	1	2023-02-09 23:49:46	1	1
477	2	100	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
478	2	101	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
479	2	102	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
481	2	103	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
483	2	104	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
485	2	105	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
490	2	108	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
492	2	109	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
498	2	1138	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
523	2	1224	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
524	2	1225	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
541	2	500	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
543	2	501	1	2022-02-22 13:09:12	1	2022-02-22 13:09:12	1	1
689	2	1077	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
690	2	1078	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
692	2	1083	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
693	2	1084	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
699	2	1090	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
703	2	106	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
704	2	110	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
705	2	111	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
706	2	112	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
707	2	113	1	2022-02-22 13:16:57	1	2022-02-22 13:16:57	1	1
1991	2	1024	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
1992	2	1025	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
1993	2	1026	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
1994	2	1027	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
1996	2	1029	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
1997	2	1030	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
1998	2	1031	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
1999	2	1032	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2000	2	1033	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2001	2	1034	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2002	2	1035	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2007	2	1040	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2008	2	1042	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2009	2	1043	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2010	2	1045	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2011	2	1046	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2012	2	1048	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2013	2	1050	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2014	2	1051	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2015	2	1052	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2016	2	1053	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2017	2	1054	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2018	2	1056	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2019	2	1057	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2020	2	1058	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2021	2	2083	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2022	2	1059	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2023	2	1060	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2024	2	1063	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2025	2	1064	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2026	2	1065	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2027	2	1066	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2028	2	1067	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2029	2	1070	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2034	2	1075	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2036	2	1082	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2037	2	1085	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2038	2	1086	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2039	2	1087	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2040	2	1088	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2041	2	1089	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2042	2	1091	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2043	2	1092	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2073	2	1139	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2074	2	115	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2075	2	1140	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2076	2	116	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2077	2	1141	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2078	2	1142	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2079	2	1143	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2100	2	1227	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2101	2	1228	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2102	2	1229	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2103	2	1237	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2104	2	1238	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2105	2	1239	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2106	2	1240	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2107	2	1241	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2108	2	1242	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2109	2	1243	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2117	2	1255	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2118	2	1256	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2119	2	1257	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2120	2	1258	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2121	2	1259	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2122	2	1260	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2123	2	1261	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2124	2	1263	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2125	2	1264	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2126	2	1265	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2127	2	1266	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2128	2	1267	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2129	2	1001	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2130	2	1002	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2131	2	1003	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2132	2	1004	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2133	2	1005	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2134	2	1006	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2135	2	1007	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2136	2	1008	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2137	2	1009	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2138	2	1010	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2139	2	1011	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2140	2	1012	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2141	2	1013	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2142	2	1014	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2143	2	1015	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2144	2	1016	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2145	2	1017	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2146	2	1018	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2147	2	1019	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2148	2	1020	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2149	2	1021	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2150	2	1022	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
2151	2	1023	1	2023-01-25 08:42:52	1	2023-01-25 08:42:52	1	1
1693	101	115	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	1	1
5888	1	115	1	2024-07-10 23:44:16.643	1	2024-07-10 23:44:16.643	1	1
5777	101	2739	1	2024-04-30 09:38:37	1	2024-04-30 09:38:37	1	1
1680	101	1238	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	1	1
5925	1	1238	1	2024-07-10 23:44:18.377	1	2024-07-10 23:44:18.377	1	1
1684	101	1242	1	2022-04-01 22:21:37	1	2022-04-01 22:21:37	1	1
5831	1	1091	1	2024-07-10 23:44:12.722	1	2024-07-10 23:44:12.722	1	1
5970	1	3101	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:12.992697	0	1
5971	1	3102	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:12.992697	0	1
5972	1	3103	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:12.992697	0	1
5973	1	3104	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:12.992697	0	1
5974	1	3105	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:12.992697	0	1
5975	1	3106	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:12.992697	0	1
5976	1	3107	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5977	1	3108	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5978	1	3109	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5979	1	3110	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5980	1	3111	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5981	1	3112	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5982	1	3113	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5983	1	3114	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5984	1	3115	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5985	1	3116	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5986	1	3117	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5987	1	3118	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5988	1	3119	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5989	1	3120	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5990	1	3121	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5991	1	3122	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5992	1	3123	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5993	1	3124	1	2026-07-21 13:00:39.505445	1	2026-07-21 13:00:39.505445	0	1
5969	1	3100	1	2026-07-21 10:53:12.992697	1	2026-07-21 10:53:12.992697	1	1
5994	1	3200	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0	1
5995	1	3201	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0	1
5996	1	3202	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0	1
5997	1	3203	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0	1
5998	1	3204	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0	1
5999	1	3205	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0	1
6000	1	3206	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0	1
6001	1	3207	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0	1
6002	1	3208	1	2026-07-28 16:09:55.955643	1	2026-07-28 16:09:55.955643	0	1
\.


--
-- Data for Name: system_sms_channel; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_sms_channel (id, signature, code, status, remark, api_key, api_secret, callback_url, creator, create_time, updater, update_time, deleted) FROM stdin;
2	Ballcat	ALIYUN	0	你要改哦，只有我可以用！！！！	LTAI5tCnKso2uG3kJ5gRav88	fGJ5SNXL7P1NHNRmJ7DJaMJGPyE55C	\N		2021-03-31 11:53:10	1	2023-12-02 22:10:17	0
4	测试渠道	DEBUG_DING_TALK	0	123	696b5d8ead48071237e4aa5861ff08dbadb2b4ded1c688a7b7c9afc615579859	SEC5c4e5ff888bc8a9923ae47f59e7ccd30af1f14d93c55b4e2c9cb094e35aeed67	\N	1	2021-04-13 00:23:14	1	2022-03-27 20:29:49	0
6	测试演示	DEBUG_DING_TALK	0	仅测试	696b5d8ead48071237e4aa5861ff08dbadb2b4ded1c688a7b7c9afc615579859	SEC5c4e5ff888bc8a9923ae47f59e7ccd30af1f14d93c55b4e2c9cb094e35aeed67	\N	1	2022-04-10 23:07:59	1	2023-12-02 22:10:08	0
\.


--
-- Data for Name: system_sms_code; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_sms_code (id, mobile, code, create_ip, scene, today_index, used, used_time, used_ip, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: system_sms_log; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_sms_log (id, channel_id, channel_code, template_id, template_code, template_type, template_content, template_params, api_template_id, mobile, user_id, user_type, send_status, send_time, api_send_code, api_send_msg, api_request_id, api_serial_no, receive_status, receive_time, api_receive_code, api_receive_msg, creator, create_time, updater, update_time, deleted) FROM stdin;
\.


--
-- Data for Name: system_sms_template; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_sms_template (id, type, status, code, name, content, params, remark, api_template_id, channel_id, channel_code, creator, create_time, updater, update_time, deleted) FROM stdin;
2	1	0	test_01	测试验证码短信	正在进行登录操作{operation}，您的验证码是{code}	["operation","code"]	测试备注	4383920	6	DEBUG_DING_TALK		2021-03-31 10:49:38	1	2023-12-02 22:32:47	0
3	1	0	test_02	公告通知	您的验证码{code}，该验证码5分钟内有效，请勿泄漏于他人！	["code"]	\N	SMS_207945135	2	ALIYUN		2021-03-31 11:56:30	1	2021-04-10 01:22:02	0
6	3	0	test-01	测试模板	哈哈哈 {name}	["name"]	f哈哈哈	4383920	6	DEBUG_DING_TALK	1	2021-04-10 01:07:21	1	2022-12-10 21:26:09	0
7	3	0	test-04	测试下	老鸡{name}，牛逼{code}	["name","code"]	哈哈哈哈	suibian	4	DEBUG_DING_TALK	1	2021-04-13 00:29:53	1	2023-12-02 22:35:34	0
8	1	0	user-sms-login	前台用户短信登录	您的验证码是{code}	["code"]	\N	4372216	6	DEBUG_DING_TALK	1	2021-10-11 08:10:00	1	2022-12-10 21:25:59	0
9	2	0	bpm_task_assigned	【工作流】任务被分配	您收到了一条新的待办任务：{processInstanceName}-{taskName}，申请人：{startUserNickname}，处理链接：{detailUrl}	["processInstanceName","taskName","startUserNickname","detailUrl"]	\N	suibian	4	DEBUG_DING_TALK	1	2022-01-21 22:31:19	1	2022-01-22 00:03:36	0
10	2	0	bpm_process_instance_reject	【工作流】流程被不通过	您的流程被审批不通过：{processInstanceName}，原因：{reason}，查看链接：{detailUrl}	["processInstanceName","reason","detailUrl"]	\N	suibian	4	DEBUG_DING_TALK	1	2022-01-22 00:03:31	1	2022-05-01 12:33:14	0
11	2	0	bpm_process_instance_approve	【工作流】流程被通过	您的流程被审批通过：{processInstanceName}，查看链接：{detailUrl}	["processInstanceName","detailUrl"]	\N	suibian	4	DEBUG_DING_TALK	1	2022-01-22 00:04:31	1	2022-03-27 20:32:21	0
12	2	0	demo	演示模板	我就是测试一下下	[]	\N	biubiubiu	6	DEBUG_DING_TALK	1	2022-04-10 23:22:49	1	2023-03-24 23:45:07	0
14	1	0	user-update-mobile	会员用户 - 修改手机	您的验证码{code}，该验证码 5 分钟内有效，请勿泄漏于他人！	["code"]		null	4	DEBUG_DING_TALK	1	2023-08-19 18:58:01	1	2023-08-19 11:34:04	0
15	1	0	user-update-password	会员用户 - 修改密码	您的验证码{code}，该验证码 5 分钟内有效，请勿泄漏于他人！	["code"]		null	4	DEBUG_DING_TALK	1	2023-08-19 18:58:01	1	2023-08-19 11:34:18	0
16	1	0	user-reset-password	会员用户 - 重置密码	您的验证码{code}，该验证码 5 分钟内有效，请勿泄漏于他人！	["code"]		null	4	DEBUG_DING_TALK	1	2023-08-19 18:58:01	1	2023-12-02 22:35:27	0
\.


--
-- Data for Name: system_social_client; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_social_client (id, name, social_type, user_type, client_id, client_secret, agent_id, status, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
1	钉钉	20	2	dingvrnreaje3yqvzhxg	i8E6iZyDvZj51JIb0tYsYfVQYOks9Cq1lgryEjFRqC79P3iJcrxEwT6Qk2QvLrLI	\N	0		2023-10-18 11:21:18	1	2023-12-20 21:28:26	1	1
2	钉钉（王土豆）	20	2	dingtsu9hpepjkbmthhw	FP_bnSq_HAHKCSncmJjw5hxhnzs6vaVDSZZn3egj6rdqTQ_hu5tQVJyLMpgCakdP	\N	0		2023-10-18 11:21:18		2023-12-20 21:28:26	1	121
3	微信公众号	31	1	wx5b23ba7a5589ecbb	2a7b3b20c537e52e74afd395eb85f61f	\N	0		2023-10-18 16:07:46	1	2023-12-20 21:28:23	1	1
43	微信小程序	34	1	wx63c280fe3248a3e7	6f270509224a7ae1296bbf1c8cb97aed	\N	0		2023-10-19 13:37:41	1	2023-12-20 21:28:25	1	1
\.


--
-- Data for Name: system_social_user; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_social_user (id, type, openid, token, raw_token_info, nickname, avatar, raw_user_info, code, state, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: system_social_user_bind; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_social_user_bind (id, user_id, user_type, social_type, social_user_id, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
\.


--
-- Data for Name: system_tenant; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_tenant (id, name, contact_user_id, contact_name, contact_mobile, status, website, package_id, expire_time, account_count, creator, create_time, updater, update_time, deleted) FROM stdin;
121	小租户	110	normal	15601691300	0	zsxq.iocoder.cn	111	2029-05-24 15:08:17.904	20	1	2022-02-22 00:56:14	1	2025-05-08 15:08:28.25	0
122	测试租户	113	test	15601691300	0	test.iocoder.cn	111	2029-04-27 00:00:00	50	1	2022-03-07 21:37:58	1	2025-05-08 15:08:49.211	0
1	Admin-IoT	\N	Admin-IoT	17321315478	0	localhost:8888	0	2099-02-19 17:14:16	9999	1	2021-01-05 17:03:47	1	2023-11-06 11:41:41	0
\.


--
-- Data for Name: system_tenant_package; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_tenant_package (id, name, status, remark, menu_ids, creator, create_time, updater, update_time, deleted) FROM stdin;
111	普通套餐	0	小功能	[1,2,5,1031,1032,1033,1034,1035,1036,1037,1038,1039,1050,1051,1052,1053,1054,1056,1057,1058,1059,1060,1063,1064,1065,1066,1067,1070,1075,1076,1077,1078,1082,1083,1084,1085,1086,1087,1088,1089,1090,1091,1092,1118,1119,1120,100,101,102,103,106,107,110,111,112,113,1138,114,1139,115,1140,116,1141,1142,1143,2713,2714,2715,2716,2717,2718,2720,1185,2721,1186,2722,1187,2723,1188,2724,1189,2725,1190,2726,1191,2727,2472,1192,2728,1193,2729,1194,2730,1195,2731,1196,2732,1197,2733,2478,1198,2734,2479,1199,2735,2480,1200,2481,1201,2482,1202,2483,2484,2485,2486,2487,1207,2488,1208,2489,1209,2490,1210,2491,1211,2492,1212,2493,1213,2494,2495,1215,1216,2497,1217,1218,1219,1220,1221,1222,1224,1225,1226,1227,1228,1229,1237,1238,1239,1240,1241,1242,1243,2525,1255,1256,1001,1257,1002,1258,1003,1259,1004,1260,1005,1006,1007,1008,1009,1010,1011,1012,1013,1014,1015,1016,1017,1018,1019,1020]	1	2022-02-22 00:54:00	1	2024-03-30 17:53:17	0
\.


--
-- Data for Name: system_user_post; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_user_post (id, user_id, post_id, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
112	1	1	admin	2022-05-02 07:25:24	admin	2022-05-02 07:25:24	0	1
113	100	1	admin	2022-05-02 07:25:24	admin	2022-05-02 07:25:24	0	1
115	104	1	1	2022-05-16 19:36:28	1	2022-05-16 19:36:28	0	1
116	117	2	1	2022-07-09 17:40:26	1	2022-07-09 17:40:26	0	1
117	118	1	1	2022-07-09 17:44:44	1	2022-07-09 17:44:44	0	1
119	114	5	1	2024-03-24 20:45:51	1	2024-03-24 20:45:51	0	1
123	115	1	1	2024-04-04 09:37:14	1	2024-04-04 09:37:14	0	1
124	115	2	1	2024-04-04 09:37:14	1	2024-04-04 09:37:14	0	1
\.


--
-- Data for Name: system_user_role; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_user_role (id, user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) FROM stdin;
1	1	1		2022-01-11 13:19:45		2022-05-12 12:35:17	0	1
2	2	2		2022-01-11 13:19:45		2022-05-12 12:35:13	0	1
4	100	101		2022-01-11 13:19:45		2022-05-12 12:35:13	0	1
5	100	1		2022-01-11 13:19:45		2022-05-12 12:35:12	0	1
6	100	2		2022-01-11 13:19:45		2022-05-12 12:35:11	0	1
10	103	1	1	2022-01-11 13:19:45	1	2022-01-11 13:19:45	0	1
14	110	109	1	2022-02-22 00:56:14	1	2022-02-22 00:56:14	0	121
15	111	110	110	2022-02-23 13:14:38	110	2022-02-23 13:14:38	0	121
16	113	111	1	2022-03-07 21:37:58	1	2022-03-07 21:37:58	0	122
18	1	2	1	2022-05-12 20:39:29	1	2022-05-12 20:39:29	0	1
20	104	101	1	2022-05-28 15:43:57	1	2022-05-28 15:43:57	0	1
22	115	2	1	2022-07-21 22:08:30	1	2022-07-21 22:08:30	0	1
35	112	1	1	2024-03-15 20:00:24	1	2024-03-15 20:00:24	0	1
38	114	101	1	2024-03-24 22:23:03	1	2024-03-24 22:23:03	0	1
39	118	2	1	2024-07-24 11:22:41.724	1	2024-07-24 11:22:41.724	0	1
36	118	1	1	2024-03-17 09:12:08	1	2024-03-17 09:12:08	1	1
\.


--
-- Data for Name: system_users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.system_users (id, username, password, nickname, remark, dept_id, post_ids, email, mobile, sex, avatar, status, login_ip, login_date, creator, create_time, updater, update_time, deleted, tenant_id, integral, do_experiment) FROM stdin;
103	yuanma	$2a$10$YMpimV4T6BtDhIaA8jSW.u8UTGBeGhc/qwXP4oxoMr4mOw9.qttt6	源码	\N	106	\N	yuanma@iocoder.cn	15601701300	0		0	0:0:0:0:0:0:0:1	2024-03-18 21:09:04		2021-01-13 23:50:35	\N	2024-03-18 21:09:04	0	1	0	0
104	test	$2a$04$KhExCYl7lx6eWWZYKsibKOZ8IBJRyuNuCcEOLQ11RYhJKgHmlSwK.	测试号	\N	107	[1,2]	111@qq.com	15601691200	1		0	0:0:0:0:0:0:0:1	2024-03-26 07:11:35		2021-01-21 02:13:53	\N	2024-03-26 07:11:35	0	1	0	0
107	admin107	$2a$10$dYOOBKMO93v/.ReCqzyFg.o67Tqk.bbc2bhrpyBGkIw9aypCtr2pm	芋艿	\N	\N	\N		15601691300	0		0		\N	1	2022-02-20 22:59:33	1	2022-02-27 08:26:51	0	118	0	0
108	admin108	$2a$10$y6mfvKoNYL1GXWak8nYwVOH.kCWqjactkzdoIDgiKl93WN3Ejg.Lu	芋艿	\N	\N	\N		15601691300	0		0		\N	1	2022-02-20 23:00:50	1	2022-02-27 08:26:53	0	119	0	0
109	admin109	$2a$10$JAqvH0tEc0I7dfDVBI7zyuB4E3j.uH6daIjV53.vUS6PknFkDJkuK	芋艿	\N	\N	\N		15601691300	0		0		\N	1	2022-02-20 23:11:50	1	2022-02-27 08:26:56	0	120	0	0
110	admin110	$2a$10$mRMIYLDtRHlf6.9ipiqH1.Z.bh/R9dO9d5iHiGYPigi6r5KOoR2Wm	小王	\N	\N	\N		15601691300	0		0	127.0.0.1	2022-09-25 22:47:33	1	2022-02-22 00:56:14	\N	2022-09-25 22:47:33	0	121	0	0
111	test	$2a$10$mRMIYLDtRHlf6.9ipiqH1.Z.bh/R9dO9d5iHiGYPigi6r5KOoR2Wm	测试用户	\N	\N	[]			0		0	0:0:0:0:0:0:0:1	2023-12-30 11:42:17	110	2022-02-23 13:14:33	\N	2023-12-30 11:42:17	0	121	0	0
112	newobject	$2a$04$dB0z8Q819fJWz0hbaLe6B.VfHCjYgWx6LFfET5lyz3JwcqlyCkQ4C	新对象	\N	100	[]		15601691235	1		0	0:0:0:0:0:0:0:1	2024-03-16 23:11:38	1	2022-02-23 19:08:03	\N	2024-03-16 23:11:38	0	1	0	0
113	aoteman	$2a$10$0acJOIk2D25/oC87nyclE..0lzeu9DtQ/n3geP4fkun/zIVRhHJIO	芋道	\N	\N	\N		15601691300	0		0	127.0.0.1	2022-03-19 18:38:51	1	2022-03-07 21:37:58	\N	2022-03-19 18:38:51	0	122	0	0
114	hrmgr	$2a$10$TR4eybBioGRhBmDBWkqWLO6NIh3mzYa8KBKDDB5woiGYFVlRAi.fu	hr 小姐姐	\N	\N	[5]		15601691236	1		0	0:0:0:0:0:0:0:1	2024-03-24 22:21:05	1	2022-03-19 21:50:58	\N	2024-03-24 22:21:05	0	1	0	0
115	aotemane	$2a$04$GcyP0Vyzb2F2Yni5PuIK9ueGxM0tkZGMtDwVRwrNbtMvorzbpNsV2	阿呆	11222	102	[1,2]	7648@qq.com	15601691229	2		0		\N	1	2022-04-30 02:55:43	1	2024-04-04 09:37:14	0	1	0	0
117	admin123	$2a$10$WI8Gg/lpZQIrOEZMHqka7OdFaD4Nx.B/qY8ZGTTUKrOJwaHFqibaC	测试号	1111	100	[2]		15601691234	1		0		\N	1	2022-07-09 17:40:26	1	2022-07-09 17:40:26	0	1	0	0
131	hh	$2a$04$jyH9h6.gaw8mpOjPfHIpx.8as2Rzfcmdlj5rlJFwgCw4rsv/MTb2K	呵呵	\N	100	[]	777@qq.com	15601882312	1		0		\N	1	2024-04-27 08:45:56	1	2024-04-27 08:45:56	1	1	0	0
100	yudao	$2a$10$11U48RhyJ5pSBYWSn12AD./ld671.ycSzJHbyrtpeoMeYiw31eo8a	王总	不要吓我	104	[1]	yudao@iocoder.cn	15601691300	1		1	127.0.0.1	2022-07-09 23:03:33		2021-01-07 09:07:17	1	2025-08-14 09:39:37.486	0	1	0	0
118	goudan	$2a$04$A7vqK6hfgoPeOGDgZoC1BOnVwTpDyBtQnlsOWBIqM8Wj9A6NTr0Tq	狗蛋	\N	103	[1]		15601691239	1		0	0:0:0:0:0:0:0:1	2024-07-24 11:23:40.932	1	2022-07-09 17:44:43	1	2025-08-14 11:34:37.076	0	1	0	0
1	admin	$2a$10$mRMIYLDtRHlf6.9ipiqH1.Z.bh/R9dO9d5iHiGYPigi6r5KOoR2Wm	IoT	管理员	103	[1]	aoteman@126.com	18818260277	1	http://test.yudao.iocoder.cn/113dcbab48d22fc988151b98be2b8b4f262effdb5305c171cf1fe9a71801377a.png	0	172.18.0.23	2026-08-03 10:15:02.650386	admin	2021-01-05 17:03:47	\N	2026-08-03 10:15:02.651936	0	1	0	0
\.


--
-- Name: dataset_image_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dataset_image_seq', 1, false);


--
-- Name: dataset_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dataset_seq', 1, false);


--
-- Name: dataset_tag_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dataset_tag_seq', 1, false);


--
-- Name: dataset_task_result_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dataset_task_result_seq', 1, false);


--
-- Name: dataset_task_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dataset_task_seq', 1, false);


--
-- Name: dataset_task_user_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dataset_task_user_seq', 1, false);


--
-- Name: dataset_video_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.dataset_video_seq', 1, false);


--
-- Name: infra_api_access_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_api_access_log_seq', 4407, true);


--
-- Name: infra_api_error_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_api_error_log_seq', 7800, true);


--
-- Name: infra_codegen_column_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_codegen_column_seq', 1278, true);


--
-- Name: infra_codegen_table_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_codegen_table_seq', 80, true);


--
-- Name: infra_config_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_config_seq', 13, false);


--
-- Name: infra_data_source_config_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_data_source_config_seq', 3, true);


--
-- Name: infra_file_config_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_file_config_seq', 23, false);


--
-- Name: infra_file_content_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_file_content_seq', 1, false);


--
-- Name: infra_file_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_file_seq', 1, true);


--
-- Name: infra_job_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_job_log_seq', 1, false);


--
-- Name: infra_job_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.infra_job_seq', 28, false);


--
-- Name: model_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.model_seq', 1, false);


--
-- Name: model_server_quantify_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.model_server_quantify_seq', 1, false);


--
-- Name: model_server_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.model_server_seq', 1, false);


--
-- Name: model_server_test_image_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.model_server_test_image_seq', 1, false);


--
-- Name: model_server_test_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.model_server_test_seq', 1, false);


--
-- Name: model_server_test_video_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.model_server_test_video_seq', 1, false);


--
-- Name: model_server_video_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.model_server_video_seq', 1, false);


--
-- Name: model_type_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.model_type_seq', 1, false);


--
-- Name: system_dept_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_dept_seq', 114, false);


--
-- Name: system_dict_data_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_dict_data_seq', 1537, false);


--
-- Name: system_dict_type_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_dict_type_seq', 620, false);


--
-- Name: system_login_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_login_log_seq', 3085, true);


--
-- Name: system_mail_account_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_mail_account_seq', 5, false);


--
-- Name: system_mail_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_mail_log_seq', 1, false);


--
-- Name: system_mail_template_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_mail_template_seq', 16, false);


--
-- Name: system_menu_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_menu_seq', 3124, true);


--
-- Name: system_notice_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_notice_seq', 5, false);


--
-- Name: system_notify_message_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_notify_message_seq', 11, false);


--
-- Name: system_notify_template_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_notify_template_seq', 1, false);


--
-- Name: system_oauth2_access_token_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_access_token_seq', 6064, true);


--
-- Name: system_oauth2_approve_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_approve_seq', 1, false);


--
-- Name: system_oauth2_client_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_client_seq', 43, false);


--
-- Name: system_oauth2_code_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_code_seq', 1, false);


--
-- Name: system_oauth2_refresh_token_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_oauth2_refresh_token_seq', 1, false);


--
-- Name: system_operate_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_operate_log_seq', 2, true);


--
-- Name: system_post_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_post_seq', 6, false);


--
-- Name: system_role_menu_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_role_menu_seq', 6002, true);


--
-- Name: system_role_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_role_seq', 112, false);


--
-- Name: system_sms_channel_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_sms_channel_seq', 7, false);


--
-- Name: system_sms_code_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_sms_code_seq', 1, false);


--
-- Name: system_sms_log_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_sms_log_seq', 1, false);


--
-- Name: system_sms_template_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_sms_template_seq', 17, false);


--
-- Name: system_social_client_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_social_client_seq', 44, false);


--
-- Name: system_social_user_bind_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_social_user_bind_seq', 1, false);


--
-- Name: system_social_user_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_social_user_seq', 1, false);


--
-- Name: system_tenant_package_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_tenant_package_seq', 112, false);


--
-- Name: system_tenant_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_tenant_seq', 123, false);


--
-- Name: system_user_post_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_user_post_seq', 125, false);


--
-- Name: system_user_role_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_user_role_seq', 39, true);


--
-- Name: system_users_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.system_users_seq', 132, false);


--
-- Name: warehouse_dataset_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.warehouse_dataset_seq', 1, false);


--
-- Name: warehouse_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.warehouse_seq', 1, false);


--
-- Name: yudao_demo01_contact_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.yudao_demo01_contact_seq', 2, false);


--
-- Name: yudao_demo02_category_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.yudao_demo02_category_seq', 7, false);


--
-- Name: yudao_demo03_course_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.yudao_demo03_course_seq', 14, false);


--
-- Name: yudao_demo03_grade_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.yudao_demo03_grade_seq', 10, false);


--
-- Name: yudao_demo03_student_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.yudao_demo03_student_seq', 10, false);


--
-- Name: infra_platform_branding infra_platform_branding_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_platform_branding
    ADD CONSTRAINT infra_platform_branding_pkey PRIMARY KEY (id);


--
-- Name: infra_api_access_log pk_infra_api_access_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_api_access_log
    ADD CONSTRAINT pk_infra_api_access_log PRIMARY KEY (id);


--
-- Name: infra_api_error_log pk_infra_api_error_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_api_error_log
    ADD CONSTRAINT pk_infra_api_error_log PRIMARY KEY (id);


--
-- Name: infra_codegen_column pk_infra_codegen_column; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_codegen_column
    ADD CONSTRAINT pk_infra_codegen_column PRIMARY KEY (id);


--
-- Name: infra_codegen_table pk_infra_codegen_table; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_codegen_table
    ADD CONSTRAINT pk_infra_codegen_table PRIMARY KEY (id);


--
-- Name: infra_config pk_infra_config; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_config
    ADD CONSTRAINT pk_infra_config PRIMARY KEY (id);


--
-- Name: infra_data_source_config pk_infra_data_source_config; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_data_source_config
    ADD CONSTRAINT pk_infra_data_source_config PRIMARY KEY (id);


--
-- Name: infra_file pk_infra_file; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_file
    ADD CONSTRAINT pk_infra_file PRIMARY KEY (id);


--
-- Name: infra_file_config pk_infra_file_config; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_file_config
    ADD CONSTRAINT pk_infra_file_config PRIMARY KEY (id);


--
-- Name: infra_file_content pk_infra_file_content; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_file_content
    ADD CONSTRAINT pk_infra_file_content PRIMARY KEY (id);


--
-- Name: infra_job pk_infra_job; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_job
    ADD CONSTRAINT pk_infra_job PRIMARY KEY (id);


--
-- Name: infra_job_log pk_infra_job_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.infra_job_log
    ADD CONSTRAINT pk_infra_job_log PRIMARY KEY (id);


--
-- Name: system_dept pk_system_dept; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dept
    ADD CONSTRAINT pk_system_dept PRIMARY KEY (id);


--
-- Name: system_dict_data pk_system_dict_data; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dict_data
    ADD CONSTRAINT pk_system_dict_data PRIMARY KEY (id);


--
-- Name: system_dict_type pk_system_dict_type; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dict_type
    ADD CONSTRAINT pk_system_dict_type PRIMARY KEY (id);


--
-- Name: system_login_log pk_system_login_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_login_log
    ADD CONSTRAINT pk_system_login_log PRIMARY KEY (id);


--
-- Name: system_mail_account pk_system_mail_account; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_mail_account
    ADD CONSTRAINT pk_system_mail_account PRIMARY KEY (id);


--
-- Name: system_mail_log pk_system_mail_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_mail_log
    ADD CONSTRAINT pk_system_mail_log PRIMARY KEY (id);


--
-- Name: system_mail_template pk_system_mail_template; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_mail_template
    ADD CONSTRAINT pk_system_mail_template PRIMARY KEY (id);


--
-- Name: system_menu pk_system_menu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_menu
    ADD CONSTRAINT pk_system_menu PRIMARY KEY (id);


--
-- Name: system_notice pk_system_notice; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notice
    ADD CONSTRAINT pk_system_notice PRIMARY KEY (id);


--
-- Name: system_notify_message pk_system_notify_message; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notify_message
    ADD CONSTRAINT pk_system_notify_message PRIMARY KEY (id);


--
-- Name: system_notify_template pk_system_notify_template; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notify_template
    ADD CONSTRAINT pk_system_notify_template PRIMARY KEY (id);


--
-- Name: system_oauth2_access_token pk_system_oauth2_access_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_access_token
    ADD CONSTRAINT pk_system_oauth2_access_token PRIMARY KEY (id);


--
-- Name: system_oauth2_approve pk_system_oauth2_approve; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_approve
    ADD CONSTRAINT pk_system_oauth2_approve PRIMARY KEY (id);


--
-- Name: system_oauth2_client pk_system_oauth2_client; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_client
    ADD CONSTRAINT pk_system_oauth2_client PRIMARY KEY (id);


--
-- Name: system_oauth2_code pk_system_oauth2_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_code
    ADD CONSTRAINT pk_system_oauth2_code PRIMARY KEY (id);


--
-- Name: system_oauth2_refresh_token pk_system_oauth2_refresh_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_oauth2_refresh_token
    ADD CONSTRAINT pk_system_oauth2_refresh_token PRIMARY KEY (id);


--
-- Name: system_operate_log pk_system_operate_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_operate_log
    ADD CONSTRAINT pk_system_operate_log PRIMARY KEY (id);


--
-- Name: system_post pk_system_post; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_post
    ADD CONSTRAINT pk_system_post PRIMARY KEY (id);


--
-- Name: system_role pk_system_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_role
    ADD CONSTRAINT pk_system_role PRIMARY KEY (id);


--
-- Name: system_role_menu pk_system_role_menu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_role_menu
    ADD CONSTRAINT pk_system_role_menu PRIMARY KEY (id);


--
-- Name: system_sms_channel pk_system_sms_channel; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_sms_channel
    ADD CONSTRAINT pk_system_sms_channel PRIMARY KEY (id);


--
-- Name: system_sms_code pk_system_sms_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_sms_code
    ADD CONSTRAINT pk_system_sms_code PRIMARY KEY (id);


--
-- Name: system_sms_log pk_system_sms_log; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_sms_log
    ADD CONSTRAINT pk_system_sms_log PRIMARY KEY (id);


--
-- Name: system_sms_template pk_system_sms_template; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_sms_template
    ADD CONSTRAINT pk_system_sms_template PRIMARY KEY (id);


--
-- Name: system_social_client pk_system_social_client; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_social_client
    ADD CONSTRAINT pk_system_social_client PRIMARY KEY (id);


--
-- Name: system_social_user pk_system_social_user; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_social_user
    ADD CONSTRAINT pk_system_social_user PRIMARY KEY (id);


--
-- Name: system_social_user_bind pk_system_social_user_bind; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_social_user_bind
    ADD CONSTRAINT pk_system_social_user_bind PRIMARY KEY (id);


--
-- Name: system_tenant pk_system_tenant; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_tenant
    ADD CONSTRAINT pk_system_tenant PRIMARY KEY (id);


--
-- Name: system_tenant_package pk_system_tenant_package; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_tenant_package
    ADD CONSTRAINT pk_system_tenant_package PRIMARY KEY (id);


--
-- Name: system_user_post pk_system_user_post; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_user_post
    ADD CONSTRAINT pk_system_user_post PRIMARY KEY (id);


--
-- Name: system_user_role pk_system_user_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_user_role
    ADD CONSTRAINT pk_system_user_role PRIMARY KEY (id);


--
-- Name: system_users pk_system_users; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_users
    ADD CONSTRAINT pk_system_users PRIMARY KEY (id);


--
-- Name: qrtz_blob_triggers qrtz_blob_triggers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_blob_triggers
    ADD CONSTRAINT qrtz_blob_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_calendars qrtz_calendars_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_calendars
    ADD CONSTRAINT qrtz_calendars_pkey PRIMARY KEY (sched_name, calendar_name);


--
-- Name: qrtz_cron_triggers qrtz_cron_triggers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_cron_triggers
    ADD CONSTRAINT qrtz_cron_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_fired_triggers qrtz_fired_triggers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_fired_triggers
    ADD CONSTRAINT qrtz_fired_triggers_pkey PRIMARY KEY (sched_name, entry_id);


--
-- Name: qrtz_job_details qrtz_job_details_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_job_details
    ADD CONSTRAINT qrtz_job_details_pkey PRIMARY KEY (sched_name, job_name, job_group);


--
-- Name: qrtz_locks qrtz_locks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_locks
    ADD CONSTRAINT qrtz_locks_pkey PRIMARY KEY (sched_name, lock_name);


--
-- Name: qrtz_paused_trigger_grps qrtz_paused_trigger_grps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_paused_trigger_grps
    ADD CONSTRAINT qrtz_paused_trigger_grps_pkey PRIMARY KEY (sched_name, trigger_group);


--
-- Name: qrtz_scheduler_state qrtz_scheduler_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_scheduler_state
    ADD CONSTRAINT qrtz_scheduler_state_pkey PRIMARY KEY (sched_name, instance_name);


--
-- Name: qrtz_simple_triggers qrtz_simple_triggers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_simple_triggers
    ADD CONSTRAINT qrtz_simple_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_simprop_triggers qrtz_simprop_triggers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_simprop_triggers
    ADD CONSTRAINT qrtz_simprop_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_triggers qrtz_triggers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_triggers
    ADD CONSTRAINT qrtz_triggers_pkey PRIMARY KEY (sched_name, trigger_name, trigger_group);


--
-- Name: idx_infra_api_access_log_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_infra_api_access_log_01 ON public.infra_api_access_log USING btree (create_time);


--
-- Name: idx_qrtz_blob_triggers_sched_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_blob_triggers_sched_name ON public.qrtz_blob_triggers USING btree (sched_name, trigger_name, trigger_group);


--
-- Name: idx_qrtz_ft_inst_job_req_rcvry; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_ft_inst_job_req_rcvry ON public.qrtz_fired_triggers USING btree (sched_name, instance_name, requests_recovery);


--
-- Name: idx_qrtz_ft_j_g; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_ft_j_g ON public.qrtz_fired_triggers USING btree (sched_name, job_name, job_group);


--
-- Name: idx_qrtz_ft_jg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_ft_jg ON public.qrtz_fired_triggers USING btree (sched_name, job_group);


--
-- Name: idx_qrtz_ft_t_g; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_ft_t_g ON public.qrtz_fired_triggers USING btree (sched_name, trigger_name, trigger_group);


--
-- Name: idx_qrtz_ft_tg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_ft_tg ON public.qrtz_fired_triggers USING btree (sched_name, trigger_group);


--
-- Name: idx_qrtz_ft_trig_inst_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_ft_trig_inst_name ON public.qrtz_fired_triggers USING btree (sched_name, instance_name);


--
-- Name: idx_qrtz_j_grp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_j_grp ON public.qrtz_job_details USING btree (sched_name, job_group);


--
-- Name: idx_qrtz_j_req_recovery; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_j_req_recovery ON public.qrtz_job_details USING btree (sched_name, requests_recovery);


--
-- Name: idx_qrtz_t_c; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_c ON public.qrtz_triggers USING btree (sched_name, calendar_name);


--
-- Name: idx_qrtz_t_g; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_g ON public.qrtz_triggers USING btree (sched_name, trigger_group);


--
-- Name: idx_qrtz_t_j; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_j ON public.qrtz_triggers USING btree (sched_name, job_name, job_group);


--
-- Name: idx_qrtz_t_jg; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_jg ON public.qrtz_triggers USING btree (sched_name, job_group);


--
-- Name: idx_qrtz_t_n_g_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_n_g_state ON public.qrtz_triggers USING btree (sched_name, trigger_group, trigger_state);


--
-- Name: idx_qrtz_t_n_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_n_state ON public.qrtz_triggers USING btree (sched_name, trigger_name, trigger_group, trigger_state);


--
-- Name: idx_qrtz_t_next_fire_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_next_fire_time ON public.qrtz_triggers USING btree (sched_name, next_fire_time);


--
-- Name: idx_qrtz_t_nft_misfire; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_nft_misfire ON public.qrtz_triggers USING btree (sched_name, misfire_instr, next_fire_time);


--
-- Name: idx_qrtz_t_nft_st; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_nft_st ON public.qrtz_triggers USING btree (sched_name, trigger_state, next_fire_time);


--
-- Name: idx_qrtz_t_nft_st_misfire; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_nft_st_misfire ON public.qrtz_triggers USING btree (sched_name, misfire_instr, next_fire_time, trigger_state);


--
-- Name: idx_qrtz_t_nft_st_misfire_grp; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_nft_st_misfire_grp ON public.qrtz_triggers USING btree (sched_name, misfire_instr, next_fire_time, trigger_group, trigger_state);


--
-- Name: idx_qrtz_t_state; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_qrtz_t_state ON public.qrtz_triggers USING btree (sched_name, trigger_state);


--
-- Name: idx_system_oauth2_access_token_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_oauth2_access_token_01 ON public.system_oauth2_access_token USING btree (access_token);


--
-- Name: idx_system_oauth2_access_token_02; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_oauth2_access_token_02 ON public.system_oauth2_access_token USING btree (refresh_token);


--
-- Name: idx_system_sms_code_01; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_sms_code_01 ON public.system_sms_code USING btree (mobile);


--
-- Name: qrtz_blob_triggers qrtz_blob_triggers_ibfk_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_blob_triggers
    ADD CONSTRAINT qrtz_blob_triggers_ibfk_1 FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES public.qrtz_triggers(sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_cron_triggers qrtz_cron_triggers_ibfk_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_cron_triggers
    ADD CONSTRAINT qrtz_cron_triggers_ibfk_1 FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES public.qrtz_triggers(sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_simple_triggers qrtz_simple_triggers_ibfk_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_simple_triggers
    ADD CONSTRAINT qrtz_simple_triggers_ibfk_1 FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES public.qrtz_triggers(sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_simprop_triggers qrtz_simprop_triggers_ibfk_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_simprop_triggers
    ADD CONSTRAINT qrtz_simprop_triggers_ibfk_1 FOREIGN KEY (sched_name, trigger_name, trigger_group) REFERENCES public.qrtz_triggers(sched_name, trigger_name, trigger_group);


--
-- Name: qrtz_triggers qrtz_triggers_ibfk_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qrtz_triggers
    ADD CONSTRAINT qrtz_triggers_ibfk_1 FOREIGN KEY (sched_name, job_name, job_group) REFERENCES public.qrtz_job_details(sched_name, job_name, job_group);


--
-- PostgreSQL database dump complete
--

\unrestrict lHKTRHsiAYh9gijjAFrIL3pbtB0G71ugGq0SDGq6TzddFa2Xib2TZ96hG3qKwvu

