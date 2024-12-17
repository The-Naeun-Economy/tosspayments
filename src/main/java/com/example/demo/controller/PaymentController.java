package com.example.demo.controller;

import com.example.demo.Dto.IsBilling;
import com.example.demo.Dto.PlantiPayDto;
import com.example.demo.Response.PaymentResponse;
import com.example.demo.Response.testPaymentResponse;
import com.example.demo.Service.KafkaService;
import com.example.demo.Service.PaymentService;
import com.example.demo.domain.Payment;
import com.example.demo.domain.User;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.view.RedirectView;
import reactor.netty.http.client.HttpClient;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@CrossOrigin("*")
@RequestMapping("/api/v1/tosspayments")
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final KafkaService kafkaService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final String WIDGET_SECRET_KEY = "test_gsk_docs_OaPz8L5KdmQXkzRz3y47BMw6";
    private static final String API_SECRET_KEY = "test_sk_ALnQvDd2VJx7LPgwxBBa8Mj7X41m";
    private final Map<String, String> billingKeyMap = new HashMap<>();
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @GetMapping("/mypayments")
    public Page<PaymentResponse> myPayments(@RequestHeader String Authorization,
                                    @RequestParam int page, @RequestParam int size
    ) {
        Long id = paymentService.tokenGetUserId(Authorization);
        Pageable pageable = PageRequest.of(page, size);
        return paymentService.getPaymentByUserId(id, pageable);
    }

    @GetMapping
    public Page<PaymentResponse> myPayments(@RequestParam int page, @RequestParam int size) {
        Pageable pageable = PageRequest.of(page, size);
        return paymentService.getPaymentAll(pageable);
    }

    @GetMapping("/between")
    public Page<PaymentResponse> findBetween (
            @RequestParam String start,//2024-12-16
            @RequestParam String end,//2024-12-16
            @RequestParam int page,
            @RequestParam int size) {
        return paymentService.getBetweenAt(start,end,page,size);
    }

    @GetMapping("/betweensum")
    public Long betweenSum (
            @RequestParam String start,//2024-12-16
            @RequestParam String end//2024-12-16
) {
        return paymentService.getBetweenSum(start,end);
    }

    @PostMapping("/plantipay")
    public RedirectView plantiPay(@RequestHeader String Authorization,
                                  @RequestParam String orderName,
                                  @RequestParam int amount) throws JsonProcessingException {
        String url = "https://plantify.co.kr/v1/pay/payment";
        JSONObject requestData = new JSONObject();
        requestData.put("userId", 2);
        requestData.put("sellerId", 2);
        requestData.put("orderName", "리픽 1개월 구독");
        requestData.put("amount", 15000);
        requestData.put("status", "PAYMENT");
        requestData.put("redirectUri", "https://repick.site");
        System.out.println(requestData);

        HttpClient httpClient = HttpClient.create().followRedirect(true);
        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        String response = webClient.post()
                .uri(url)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(objectMapper.writeValueAsString(requestData))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        System.out.println(response);

        return new RedirectView(response);
    }

    @GetMapping("/remaining")
    public String userRemaining(@RequestHeader String Authorization) {
        Long id = paymentService.tokenGetUserId(Authorization);
        return paymentService.getRemaining(id);
    }

    @RequestMapping(value = {"/confirm/widget", "/confirm/payment"})
    public ResponseEntity<JSONObject> confirmPayment(
            HttpServletRequest request,
            @RequestHeader String Authorization,
            @RequestBody String jsonBody
    ) throws Exception {
        String secretKey = request.getRequestURI().contains("/confirm/payment") ? API_SECRET_KEY : WIDGET_SECRET_KEY;
        JSONObject response = sendRequest(parseRequestData(jsonBody), secretKey, "https://api.tosspayments.com/v1/payments/confirm");

        if (!response.containsKey("code")) {

            Long id = paymentService.tokenGetUserId(Authorization);
            ZonedDateTime parsedDateTime = ZonedDateTime.parse(response.get("requestedAt").toString());
            int days = response.get("totalAmount").toString().equals("5900") ? 30 : 365;

            userRepository.findById(id).ifPresentOrElse(user -> {
                ZonedDateTime newDateTime = user.getRemaining().plusDays(days);
                user.setRemaining(newDateTime);
                Payment payment = testPaymentResponse.toEntity(response, user);
                paymentRepository.save(payment);
                user.getPayments().add(payment);
                userRepository.save(user);
            }, () -> {
                ZonedDateTime newDateTime = parsedDateTime.plusDays(days);
                Set<Payment> payments = new HashSet<>();
                User newUser = new User(id, newDateTime, payments);
                userRepository.save(newUser);
                Payment payment = testPaymentResponse.toEntity(response, newUser);
                paymentRepository.save(payment);
                payments.add(payment);
                userRepository.save(newUser);
            });
            try {
                kafkaService.sendMessage(new IsBilling(id, true));
            } catch (Exception e) {
                logger.error("Failed to Producer Sent", e);
            }
        }

        int statusCode = response.containsKey("code") ? 400 : 200;
        return ResponseEntity.status(statusCode).body(response);
    }


    @RequestMapping(value = "/confirm-billing")
    public ResponseEntity<JSONObject> confirmBilling(@RequestBody String jsonBody) throws Exception {
        JSONObject requestData = parseRequestData(jsonBody);
        String billingKey = billingKeyMap.get(requestData.get("customerKey"));
        JSONObject response = sendRequest(requestData, API_SECRET_KEY, "https://api.tosspayments.com/v1/billing/" + billingKey);
        return ResponseEntity.status(response.containsKey("error") ? 400 : 200).body(response);
    }

    @RequestMapping(value = "/issue-billing-key")
    public ResponseEntity<JSONObject> issueBillingKey(@RequestBody String jsonBody) throws Exception {
        JSONObject requestData = parseRequestData(jsonBody);
        JSONObject response = sendRequest(requestData, API_SECRET_KEY, "https://api.tosspayments.com/v1/billing/authorizations/issue");

        if (!response.containsKey("error")) {
            billingKeyMap.put((String) requestData.get("customerKey"), (String) response.get("billingKey"));
        }

        return ResponseEntity.status(response.containsKey("error") ? 400 : 200).body(response);
    }

    @RequestMapping(value = "/callback-auth", method = RequestMethod.GET)
    public ResponseEntity<JSONObject> callbackAuth(@RequestParam String customerKey, @RequestParam String code) throws Exception {
        JSONObject requestData = new JSONObject();
        requestData.put("grantType", "AuthorizationCode");
        requestData.put("customerKey", customerKey);
        requestData.put("code", code);
        
        String url = "https://api.tosspayments.com/v1/brandpay/authorizations/access-token";
        JSONObject response = sendRequest(requestData, API_SECRET_KEY, url);

        logger.info("Response Data: {}", response);

        return ResponseEntity.status(response.containsKey("error") ? 400 : 200).body(response);
    }

    @RequestMapping(value = "/confirm/brandpay", method = RequestMethod.POST, consumes = "application/json")
    public ResponseEntity<JSONObject> confirmBrandpay(@RequestBody String jsonBody) throws Exception {
        JSONObject requestData = parseRequestData(jsonBody);
        String url = "https://api.tosspayments.com/v1/brandpay/payments/confirm";
        JSONObject response = sendRequest(requestData, API_SECRET_KEY, url);
        return ResponseEntity.status(response.containsKey("error") ? 400 : 200).body(response);
    }

    private JSONObject parseRequestData(String jsonBody) {
        try {
            return (JSONObject) new JSONParser().parse(jsonBody);
        } catch (ParseException e) {
            logger.error("JSON Parsing Error", e);
            return new JSONObject();
        }
    }

    private JSONObject sendRequest(JSONObject requestData, String secretKey, String urlString) throws IOException {
        HttpURLConnection connection = createConnection(secretKey, urlString);
        try (OutputStream os = connection.getOutputStream()) {
            os.write(requestData.toString().getBytes(StandardCharsets.UTF_8));
        }

        try (InputStream responseStream = connection.getResponseCode() == 200 ? connection.getInputStream() : connection.getErrorStream();
             Reader reader = new InputStreamReader(responseStream, StandardCharsets.UTF_8)) {
            return (JSONObject) new JSONParser().parse(reader);
        } catch (Exception e) {
            logger.error("Error reading response", e);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("error", "Error reading response");
            return errorResponse;
        }
    }

    private HttpURLConnection createConnection(String secretKey, String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8)));
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        return connection;
    }

    @RequestMapping(value = "/fail", method = RequestMethod.GET)
    public String failPayment(HttpServletRequest request, Model model) {
        model.addAttribute("code", request.getParameter("code"));
        model.addAttribute("message", request.getParameter("message"));
        return "/fail";
    }
}
