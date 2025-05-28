package ar.com.flow.facebook.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "facebook")
data class FacebookConfig(
    var app: App = App(),
    var redirect: Redirect = Redirect(),
    var oauth: OAuth = OAuth()
) {
    data class App(
        var id: String = "",
        var secret: String = ""
    )

    data class Redirect(
        var uri: String = ""
    )

    data class OAuth(
        var scope: String = "public_profile,email"
    )
}
