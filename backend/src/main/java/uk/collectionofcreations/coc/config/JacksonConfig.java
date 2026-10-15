package uk.collectionofcreations.coc.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.format.DateTimeFormatter;

/**
 * 时间序列化：统一输出 "yyyy-MM-dd HH:mm:ss"（前端展示友好，避免 ISO 带 T）。
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer cocDateTimeCustomizer() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(DATE_PATTERN);
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(java.time.LocalDateTime.class, new LocalDateTimeSerializer(dateTime));
            module.addDeserializer(java.time.LocalDateTime.class, new LocalDateTimeDeserializer(dateTime));
            module.addSerializer(java.time.LocalDate.class, new LocalDateSerializer(date));
            module.addDeserializer(java.time.LocalDate.class, new LocalDateDeserializer(date));
            module.addSerializer(java.sql.Timestamp.class, new com.fasterxml.jackson.databind.JsonSerializer<>() {
                @Override
                public void serialize(java.sql.Timestamp value, com.fasterxml.jackson.core.JsonGenerator gen,
                                      com.fasterxml.jackson.databind.SerializerProvider provider) throws java.io.IOException {
                    gen.writeString(value.toLocalDateTime().format(dateTime));
                }
            });
            builder.modules(module);
        };
    }
}
