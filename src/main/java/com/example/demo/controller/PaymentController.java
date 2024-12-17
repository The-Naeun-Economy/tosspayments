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
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.*;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
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
    public String plantiPay(@RequestParam int amount) throws JsonProcessingException {
        if(amount != 5900 && amount != 59000) {return "error";}
        String url = "https://plantify.co.kr/v1/pay/payment";

        Map<String, Object> requestData = new HashMap<>();
        requestData.put("userId", 2);
        requestData.put("sellerId", 2);
        requestData.put("orderName", amount == 5900 ? "리픽 1개월 구독" : "리픽 12개월 구독");
        requestData.put("amount", amount);
        requestData.put("status", "PAYMENT");
        requestData.put("redirectUri", "https://repick.site/tosspayments/loading");

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestData), headers);
        String response = restTemplate.postForObject(url, entity, String.class);

        System.out.println(response);
        return response;
    }

    @GetMapping("/plantipay")
    public String plantiPay(@RequestHeader String Authorization,
                            @RequestParam String orderId) {
        String url = "https://plantify.co.kr/v1/pay/settlements/external?orderId=" + orderId;
        RestTemplate restTemplate = new RestTemplate();
        try {
            String response = restTemplate.getForObject(url, String.class);
            System.out.println(response);
            JsonNode jsonNode = objectMapper.readTree(response);
            Long amount = jsonNode.get("amount").asLong();
            Long id = paymentService.tokenGetUserId(Authorization);
            JSONObject res = new JSONObject();
            res.put("orderId", orderId);
            res.put("paymentKey","null");
            res.put("requestedAt", ZonedDateTime.now().plusHours(9).toString());
            res.put("method", "plantiPay");
            res.put("totalAmount", amount);
            paymentService.UserIfPresentOrElse(id,res);
            try {
                kafkaService.sendMessage(new IsBilling(id, true));
            } catch (Exception e) {
                logger.error("Failed to Producer Sent", e);
            }
            return "success";
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            return "fail";
        }
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
            paymentService.UserIfPresentOrElse(id,response);
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
