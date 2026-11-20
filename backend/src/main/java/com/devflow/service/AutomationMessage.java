package com.devflow.service;

/**
 * 自动化规则的通知载荷。
 *
 * 既有 fire() 把标题/正文/链接写死成"规则 X 触发：对象标题"，设计稿这类需要带
 * 需求、负责人、版本对比入口的事件无法表达。这里由调用方给出完整文案，
 * 规则名仍由服务的自动追加，保证"哪条规则触发的"可追溯。
 */
public record AutomationMessage(String title, String content, String actionUrl) {
}
