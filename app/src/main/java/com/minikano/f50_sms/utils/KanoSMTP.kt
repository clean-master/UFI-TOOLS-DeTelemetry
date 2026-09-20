package com.minikano.f50_sms.utils
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class KanoSMTP(
    private val smtpHost: String,
    private val smtpPort: String,
    private val username: String,
    private val password: String,
    /**
     * 信头发件人（From）。SMTP 认证用户名与发件邮箱常常不是同一个值：
     * Resend 的用户名固定是 `resend`、Mailjet 用 API Key 当用户名、
     * SMTP2GO 的用户名只是中继凭据，而真正的发件地址必须是服务商后台已验证的发件域/地址。
     * 为空时回退为 [username]，以兼容历史配置（自建邮箱/QQ邮箱等用户名即发件人的场景）。
     */
    private val fromAddress: String = "",
    /** 发件人显示名（可选），如 "UFI-TOOLS" */
    private val fromName: String = "",
) {
    companion object {
        // 单线程串行发送：限制并发（最多1线程，空闲30秒后回收），排队而非丢弃。
        // 必须跨实例共享——调用方每次转发都会 new 一个 KanoSMTP
        private val sender = ThreadPoolExecutor(0, 1, 30L, TimeUnit.SECONDS, LinkedBlockingQueue())

        // 连接建立即握手（隐式 SSL/TLS）的端口，其余端口一律按 STARTTLS（显式升级）处理。
        // 465/2465 → 通用 / Resend；8465/443 → SMTP2GO；588 → Mailjet
        private val IMPLICIT_SSL_PORTS = setOf("465", "2465", "8465", "443", "588")
    }

    /** 实际发件地址：优先使用单独配置的 From，未配置则回退到认证用户名 */
    private fun resolveFrom(): String = fromAddress.trim().ifEmpty { username.trim() }

    fun sendEmail(to: String, subject: String, body: String,isHTML:Boolean=true) {
        sender.execute {
            val from = resolveFrom()
            try {
                val props = Properties()
                props["mail.smtp.auth"] = "true"
                props["mail.smtp.host"] = smtpHost
                props["mail.smtp.port"] = smtpPort
                // JavaMail 默认超时为无限，目标不可达时发送线程会永久挂起并逐渐堆积
                props["mail.smtp.connectiontimeout"] = "10000"
                props["mail.smtp.timeout"] = "15000"
                props["mail.smtp.writetimeout"] = "15000"
                // 部分老设备默认只协商到 TLSv1，会被 Resend / SMTP2GO 等现代服务商直接拒绝
                // （No appropriate protocol）。显式锁定 TLSv1.2 及以上。
                props["mail.smtp.ssl.protocols"] = "TLSv1.2"

                if (smtpPort.trim() in IMPLICIT_SSL_PORTS) {
                    // 隐式 SSL/TLS：连接一建立就完成握手
                    props["mail.smtp.ssl.enable"] = "true"
                    props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
                    props["mail.smtp.socketFactory.port"] = smtpPort.trim()
                    props["mail.smtp.socketFactory.fallback"] = "false"
                } else {
                    // 显式 STARTTLS：先明文连接，再升级为加密
                    props["mail.smtp.starttls.enable"] = "true"
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(username, password)
                    }
                })


                val message = MimeMessage(session).apply {
                    if (fromName.isNotBlank()) {
                        setFrom(InternetAddress(from, fromName.trim(), "utf-8"))
                    } else {
                        setFrom(InternetAddress(from))
                    }
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                    setSubject(subject)
                    if(isHTML) {
                        setContent(body,"text/html; charset=utf-8")
                    }
                    else {
                        setText(body)
                    }
                }

                KanoLog.d("UFI_TOOLS_LOG", "开始发送邮件...（发件人：$from / 认证用户名：$username）")
                Transport.send(message)
                KanoLog.d("UFI_TOOLS_LOG", "$from 邮件发送成功")

            } catch (e: Exception) {
                KanoLog.e(
                    "UFI_TOOLS_LOG",
                    "邮件发送失败（发件人：$from / 认证用户名：$username / 服务器：$smtpHost:$smtpPort）：${e.message}。"
                        + "若提示发件地址或域名未验证，请在邮件服务商后台完成发件域/发件地址验证；"
                        + "若认证失败，请确认「用户名」填的是服务商提供的 SMTP 凭据而非发件邮箱。",
                    e
                )
            }
        }
    }
}
