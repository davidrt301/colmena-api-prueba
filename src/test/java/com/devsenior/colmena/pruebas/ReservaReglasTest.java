package com.devsenior.colmena.pruebas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Levanta la API completa con una base de datos en memoria y le hace peticiones HTTP,
// igual que Swagger o Postman. Cada @Test es una fila de la matriz de pruebas.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReservaReglasTest {

    @Value("${local.server.port}")
    private int puerto;

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    // Fecha lejana: asi la prueba no choca con las reservas sembradas
    private final LocalDate dia = LocalDate.now().plusDays(30);

    @Test
    void lauraReservaDosHorasYElBackendCalculaElValor() throws Exception {
        HttpResponse<String> respuesta = reservar(token("laura@correo.com"), 1, "09:00", "11:00", 4);

        assertEquals(201, respuesta.statusCode(), "Una reserva valida deberia responder 201");
        assertEquals(90000, json.readTree(respuesta.body()).get("valorTotal").asInt(),
                "2 horas de Sala Panal a $ 45.000 deberian valer $ 90.000");
    }

    @Test
    void dosReservasNoSePuedenCruzarEnElMismoEspacio() throws Exception {
        reservar(token("laura@correo.com"), 2, "14:00", "16:00", 2);

        HttpResponse<String> segunda = reservar(token("andres@correo.com"), 2, "15:00", "17:00", 2);

        assertEquals(409, segunda.statusCode(), "La segunda reserva se cruza a las 15:00: deberia responder 409");
    }

    @Test
    void reservarFueraDelHorarioResponde400() throws Exception {
        HttpResponse<String> respuesta = reservar(token("laura@correo.com"), 1, "20:00", "22:00", 2);

        assertEquals(400, respuesta.statusCode(), "El coworking cierra a las 9:00 p. m.: deberia responder 400");
    }

    @Test
    void laAdministracionNoReserva() throws Exception {
        HttpResponse<String> respuesta = reservar(token("admin@colmena.co"), 1, "09:00", "10:00", 2);

        assertEquals(403, respuesta.statusCode(), "Mariana no tiene RESERVAS_CREAR: deberia responder 403");
    }

    // ---------- Ayudas: iniciar sesion y reservar por HTTP ----------

    private String token(String email) throws Exception {
        String cuerpo = "{\"email\":\"" + email + "\",\"password\":\"colmena123\"}";
        HttpResponse<String> respuesta = enviar("/api/auth/login", null, cuerpo);
        return json.readTree(respuesta.body()).get("token").asString();
    }

    private HttpResponse<String> reservar(String token, long espacioId, String inicio, String fin, int asistentes)
            throws Exception {
        String cuerpo = "{\"espacioId\":" + espacioId + ",\"fecha\":\"" + dia + "\",\"horaInicio\":\"" + inicio
                + "\",\"horaFin\":\"" + fin + "\",\"asistentes\":" + asistentes + "}";
        return enviar("/api/reservas", token, cuerpo);
    }

    private HttpResponse<String> enviar(String ruta, String token, String cuerpo) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        return http.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }
}