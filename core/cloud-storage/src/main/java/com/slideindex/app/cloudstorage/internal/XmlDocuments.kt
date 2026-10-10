package com.slideindex.app.cloudstorage.internal

import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * WebDAV PROPFIND 与 S3 ListObjectsV2 的 XML 解析基础设施。
 *
 * 刻意选择 DOM（`javax.xml.parsers`）而不是 Android 的 `XmlPullParser`：
 * 1. DOM 在 JVM 上有真实实现，解析逻辑可以写**纯 JUnit 测试**（不需要 Robolectric）；
 * 2. 两个响应体规模都很小（单层目录列表，保留策略下最多几十个备份），不必流式解析。
 *
 * 所有取值都按 **local name** 匹配：WebDAV 服务端的命名空间前缀五花八门
 * （`D:` / `d:` / `lp1:`），只按前缀匹配会在某些网盘上直接解析不到。
 */
internal object XmlDocuments {
    fun newBuilder(): DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        // 远端返回的 XML 属于不可信输入：尽量关掉 DTD 与外部实体。
        // 不同实现的特性名支持不一，任何一项不被支持都不能影响解析本身。
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.isXIncludeAware = false }
        return factory.newDocumentBuilder()
    }

    /** 解析失败返回 null，由调用方决定如何报错（避免把 XML 异常直接抛给用户）。 */
    fun parse(bytes: ByteArray): Document? =
        runCatching { newBuilder().parse(ByteArrayInputStream(bytes)) }.getOrNull()

    fun childElements(parent: Element): List<Element> {
        val nodes = parent.childNodes
        val result = ArrayList<Element>(nodes.length)
        for (index in 0 until nodes.length) {
            (nodes.item(index) as? Element)?.let(result::add)
        }
        return result
    }

    /** 取直接子元素里第一个 local name 匹配的节点文本。 */
    fun childText(parent: Element, localName: String): String? =
        childElements(parent).firstOrNull { it.localNameOrTag() == localName }?.textContent?.trim()

    /** 取所有后代里 local name 匹配的节点（顺序按文档顺序）。 */
    fun descendants(parent: Element, localName: String): List<Element> {
        val nodes = parent.getElementsByTagNameNS("*", localName)
        if (nodes.length > 0) {
            return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
        }
        // 少数实现不认 "*" 命名空间通配，退化成按标签名匹配。
        val fallback = parent.getElementsByTagName(localName)
        return (0 until fallback.length).mapNotNull { fallback.item(it) as? Element }
    }

    /** 直接子元素里 local name 匹配的节点集合。 */
    fun children(parent: Element, localName: String): List<Element> =
        childElements(parent).filter { it.localNameOrTag() == localName }

    /**
     * 命名空间感知开启时 `localName` 才非空；退化路径下按 `tagName` 去前缀。
     *
     * 用 `this.` 显式限定：本文件多个函数都有一个叫 `localName` 的参数，
     * 不限定的话扩展体里的 `localName` 会被外层参数遮蔽。
     */
    private fun Element.localNameOrTag(): String =
        this.localName ?: this.tagName.substringAfterLast(':')
}
