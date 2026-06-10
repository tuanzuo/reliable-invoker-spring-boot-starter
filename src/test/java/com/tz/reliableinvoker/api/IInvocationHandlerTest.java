package com.tz.reliableinvoker.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IInvocationHandler 单元测试（示例实现）
 *
 * @author tuanzuo use AI
 * @time 2026-06-10 00:00:00
 * @version 1.0.0-SNAPSHOT
 */
public class IInvocationHandlerTest {

    enum TestScene {
        ORDER,
        PAYMENT
    }

    @Test
    void testGetSceneReturnsCorrectValue() {
        IInvocationHandler<TestScene, String> handler = new TestOrderHandler();

        assertEquals(TestScene.ORDER, handler.getScene());
    }

    @Test
    void testExecuteWithValidParams() {
        IInvocationHandler<TestScene, String> handler = new TestOrderHandler();

        String result = handler.execute("{\"orderId\":123}");

        assertEquals("processed: {\"orderId\":123}", result);
    }

    @Test
    void testExecuteWithNullParams() {
        IInvocationHandler<TestScene, String> handler = new TestOrderHandler();

        String result = handler.execute(null);

        assertEquals("processed: null", result);
    }

    @Test
    void testExecuteWithEmptyParams() {
        IInvocationHandler<TestScene, String> handler = new TestOrderHandler();

        String result = handler.execute("");

        assertEquals("processed: ", result);
    }

    @Test
    void testDifferentHandlerDifferentScene() {
        IInvocationHandler<TestScene, String> orderHandler = new TestOrderHandler();
        IInvocationHandler<TestScene, String> paymentHandler = new TestPaymentHandler();

        assertEquals(TestScene.ORDER, orderHandler.getScene());
        assertEquals(TestScene.PAYMENT, paymentHandler.getScene());
        assertEquals("processed: {}", orderHandler.execute("{}"));
        assertEquals("payment-result", paymentHandler.execute("{}"));
    }

    @Test
    void testHandlerImplementsInterface() {
        TestOrderHandler handler = new TestOrderHandler();

        assertTrue(handler instanceof IInvocationHandler);
    }

    /**
     * 测试用的订单处理器
     */
    static class TestOrderHandler implements IInvocationHandler<TestScene, String> {

        @Override
        public TestScene getScene() {
            return TestScene.ORDER;
        }

        @Override
        public String execute(String paramsJson) {
            return "processed: " + paramsJson;
        }
    }

    /**
     * 测试用的支付处理器
     */
    static class TestPaymentHandler implements IInvocationHandler<TestScene, String> {

        @Override
        public TestScene getScene() {
            return TestScene.PAYMENT;
        }

        @Override
        public String execute(String paramsJson) {
            return "payment-result";
        }
    }
}
