package org.example.route;

import org.apache.camel.builder.RouteBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.jackson.JacksonDataFormat;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;



@ApplicationScoped
public class KafkaToLogRoute extends RouteBuilder {

    @Produces
    @Named("sslContextParameters")
    public SSLContextParameters createSslContextParameters() {
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        
        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        trustManagersParameters.setTrustManager(new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        });
        sslContextParameters.setTrustManagers(trustManagersParameters);
        
        return sslContextParameters;
    }

    @Override
    public void configure() {
        @SuppressWarnings("resource")
        JacksonDataFormat jsonDataFormat = new JacksonDataFormat();
        jsonDataFormat.setPrettyPrint(false);


        try {
            from("kafka:my-topic10?brokers=cluster-nonprod01-kafka-bootstrap.amq-streams-kafka:9092")
                .routeId("kafka-jslt-log")
                .log("JSON de entrada: ${body}")
                .log("URL DE LA PETICION: ${header.CamelHttpUrl}")
                .log("HEADERS: ${headers}")
//                .setHeader("Content-Type", constant("application/vnd.kafka.json.v2+json"))
                .setHeader("Accept", constant("application/json"))
//                .setHeader("user_key", constant("c42e2d875cc2712506851a7cc228c133"))
//                 .to("https://prdct-transact-env0-test-3scale-apicast-staging.apps.os-nonprod.domcoin.net/CreateLoan?httpMethod=POST&sslContextParameters=#sslContextParameters&throwExceptionOnFailure=false")
                .to("https://api-gtc-env0-core-t24.apps.os-nonprod.domcoin.net/ONB-002_loanCreate-provider-container/api/v1.0.0/holdings/loans?httpMethod=POST&sslContextParameters=#sslContextParameters&throwExceptionOnFailure=false")
                .doTry()
                .log("Código de respuesta: ${header.CamelHttpResponseCode}")
                .choice()
                    .when(header("CamelHttpResponseCode").isLessThan(400))
                        .log("Respuesta exitosa de la API: ${body}")
                    .otherwise()
                        .log(LoggingLevel.ERROR, "Error HTTP ${header.CamelHttpResponseCode}: ${body}")
                .end()
                .doTry()
                    .to("jslt:jsonResponse.jslt")
                .doCatch(Exception.class)
                .process(exchange -> {
                        Exception exception = exchange.getProperty(Exception.class.getName(), Exception.class);
                        exchange.getMessage().setBody("Error: " + (exception != null ? exception.getMessage() : "Unknown"));
                    })
                .end()
                .log("Error ${exception.message}")
                //enviar la respuesta a kafka
                .to("kafka:my-topic10-response?brokers=cluster-nonprod01-kafka-bootstrap.amq-streams-kafka:9092")
                //ver en logs
                .log("Respuesta de la api: ${body}");
                
            } catch(Exception e) {
                    System.err.println("Error al configurar la ruta: " + e.getMessage());
                    e.printStackTrace();
                }
        }
    }
