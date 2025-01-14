package com.clevercloud.biscuitpulsar;

import com.clevercloud.biscuit.crypto.KeyPair;
import com.clevercloud.biscuit.datalog.SymbolTable;
import com.clevercloud.biscuit.error.Error;
import com.clevercloud.biscuit.token.Biscuit;
import com.clevercloud.biscuit.token.builder.Block;
import org.apache.pulsar.broker.ServiceConfiguration;
import org.apache.pulsar.broker.authentication.AuthenticationDataSource;
import org.apache.pulsar.common.naming.NamespaceName;
import org.apache.pulsar.common.naming.TopicName;
import org.apache.pulsar.common.policies.data.NamespaceOperation;
import org.apache.pulsar.common.policies.data.PolicyName;
import org.apache.pulsar.common.policies.data.PolicyOperation;
import org.apache.pulsar.common.policies.data.TopicOperation;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.AuthenticationException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static com.clevercloud.biscuit.crypto.TokenSignature.hex;
import static com.clevercloud.biscuitpulsar.formatter.BiscuitFormatter.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthorizationProviderBiscuitTest {
    static final Logger log = LoggerFactory.getLogger(AuthorizationProviderBiscuitTest.class);

    private String authedBiscuit(KeyPair root, Biscuit biscuit) throws IOException, AuthenticationException {
        AuthenticationProviderBiscuit provider = new AuthenticationProviderBiscuit();
        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderBiscuit.CONF_BISCUIT_PUBLIC_ROOT_KEY, hex(root.public_key().key.getAbyte()));
        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);
        provider.initialize(conf);

        return provider.authenticate(new AuthenticationDataSource() {
            @Override
            public boolean hasDataFromCommand() {
                return true;
            }

            @Override
            public String getCommandData() {
                try {
                    return biscuit.serialize_b64url();
                } catch (Error.FormatError.SerializationError e) {
                    log.error("Can't deserialize biscuit due to: {}", e.getMessage());
                    return "";
                }
            }
        });
    }

    @Test
    public void testProduceAndNotConsumeOnTopic() throws Exception {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";
        String topic = "topicTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        block0.add_check(topicOperationCheck(TopicName.get(tenant + "/" + namespace + "/" + topic), TopicOperation.PRODUCE));
        Biscuit biscuit = Biscuit.make(rng, root, symbols, block0.build());

        String authedBiscuit = authedBiscuit(root, biscuit);
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        log.debug(biscuit.print());
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + topic), authedBiscuit, TopicOperation.PRODUCE, null));
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + topic), authedBiscuit, TopicOperation.CONSUME, null));
    }

    @Test
    public void testProduceAndNotConsumeAttenuatedOnTopic() throws Exception {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";
        String topic = "topicTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit rootBiscuit = Biscuit.make(rng, root, symbols, block0.build());

        Block block1 = rootBiscuit.create_block();
        block1.add_check(topicOperationCheck(TopicName.get(tenant + "/" + namespace + "/" + topic), TopicOperation.PRODUCE));
        Biscuit biscuit = rootBiscuit.attenuate(rng, root, block1.build());

        String authedBiscuit = authedBiscuit(root, biscuit);
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        log.debug(biscuit.print());
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + topic), authedBiscuit, TopicOperation.PRODUCE, null));
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + topic), authedBiscuit, TopicOperation.CONSUME, null));
    }

    @Test
    public void testConsumeAndNotProduceOnTopic() throws Exception {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";
        String topic = "topicTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        block0.add_check(topicOperationCheck(TopicName.get(tenant + "/" + namespace + "/" + topic), TopicOperation.CONSUME));
        Biscuit biscuit = Biscuit.make(rng, root, symbols, block0.build());

        String authedBiscuit = authedBiscuit(root, biscuit);
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        log.debug(biscuit.print());
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + topic), authedBiscuit, TopicOperation.PRODUCE, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + topic), authedBiscuit, TopicOperation.CONSUME, null));
    }

    @Test
    public void testConsumerAndNotProduceAttenuatedOnTopic() throws Exception {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";
        String topic = "topicTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit rootBiscuit = Biscuit.make(rng, root, symbols, block0.build());

        Block block1 = rootBiscuit.create_block();
        block1.add_check(topicOperationCheck(TopicName.get(tenant + "/" + namespace + "/" + topic), TopicOperation.CONSUME));
        Biscuit biscuit = rootBiscuit.attenuate(rng, root, block1.build());

        String authedBiscuit = authedBiscuit(root, biscuit);
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + topic), authedBiscuit, TopicOperation.PRODUCE, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + topic), authedBiscuit, TopicOperation.CONSUME, null));
    }

    @Test
    public void testTopicCreation() throws Exception {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        block0.add_check("check if namespace_operation(\"create_topic\")");
        Biscuit biscuit = Biscuit.make(rng, root, symbols, block0.build());

        String authedBiscuit = authedBiscuit(root, biscuit);
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        log.debug(biscuit.print());
        assertTrue(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.CREATE_TOPIC, null));
        assertFalse(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.DELETE_TOPIC, null));
    }

    @Test
    public void testReadWriteTopicInNamespace() throws Exception {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        block0.add_check("check if " + topicVariableFact(NamespaceName.get(tenant + "/" + namespace)) + ", topic_operation($operation), [\"produce\",\"consume\"].contains($operation)");
        Biscuit biscuit = Biscuit.make(rng, root, symbols, block0.build());

        String authedBiscuit = authedBiscuit(root, biscuit);
        log.debug(biscuit.print());
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.CONSUME, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + "test123"), authedBiscuit, TopicOperation.CONSUME, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.PRODUCE, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + "test123"), authedBiscuit, TopicOperation.PRODUCE, null));
    }

    @Test
    public void testTopicOperation() throws IOException, AuthenticationException, ExecutionException, InterruptedException, Error, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit rootBiscuit = Biscuit.make(rng, root, symbols, block0.build());

        Block block1 = rootBiscuit.create_block();
        block1.add_check("check if " + topicVariableFact(tenant, namespace) + ", topic_operation($4)");
        Biscuit biscuit = rootBiscuit.attenuate(rng, root, block1.build());

        String authedBiscuit = authedBiscuit(root, biscuit);
        log.debug(biscuit.print());
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        assertTrue(authorizationProvider.allowTopicOperationAsync(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.PRODUCE, null).get());
        assertTrue(authorizationProvider.allowTopicOperationAsync(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.CONSUME, null).get());
    }

    @Test
    public void testLimitations() throws Exception {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit rootBiscuit = Biscuit.make(rng, root, symbols, block0.build());

        Block block1 = rootBiscuit.create_block();
        block1.add_check("check if " + namespaceFact(tenant, namespace) + " or " + topicVariableFact(tenant, namespace));
        Biscuit biscuit = rootBiscuit.attenuate(rng, root, block1.build());

        String authedBiscuit = authedBiscuit(root, biscuit);
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();

        log.debug(biscuit.print());
        assertTrue(authorizationProvider.allowNamespacePolicyOperation(NamespaceName.get(tenant + "/" + namespace), PolicyName.COMPACTION, PolicyOperation.WRITE, authedBiscuit, null));
        assertTrue(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.CREATE_TOPIC, null));
        assertTrue(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.GET_TOPIC, null));
        assertTrue(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.GET_TOPICS, null));
        assertTrue(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.DELETE_TOPIC, null));
        assertFalse(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/random-ns"), authedBiscuit, NamespaceOperation.DELETE_TOPIC, null));
        assertFalse(authorizationProvider.allowNamespaceOperation(NamespaceName.get("random-tenant/" + namespace), authedBiscuit, NamespaceOperation.DELETE_TOPIC, null));
        assertFalse(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.ADD_BUNDLE, null));
        assertFalse(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.DELETE_BUNDLE, null));
        assertFalse(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.GET_BUNDLE, null));
        assertTrue(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.CLEAR_BACKLOG, null));
        assertTrue(authorizationProvider.allowNamespaceOperation(NamespaceName.get(tenant + "/" + namespace), authedBiscuit, NamespaceOperation.UNSUBSCRIBE, null));
        assertTrue(authorizationProvider.allowNamespacePolicyOperation(NamespaceName.get(tenant + "/" + namespace), PolicyName.ALL, PolicyOperation.READ, authedBiscuit, null));
        assertTrue(authorizationProvider.allowNamespacePolicyOperation(NamespaceName.get(tenant + "/" + namespace), PolicyName.TTL, PolicyOperation.READ, authedBiscuit, null));
        assertFalse(authorizationProvider.allowNamespacePolicyOperation(NamespaceName.get(tenant + "/" + namespace), PolicyName.OFFLOAD, PolicyOperation.WRITE, authedBiscuit, null));
        assertTrue(authorizationProvider.allowNamespacePolicyOperation(NamespaceName.get(tenant + "/" + namespace), PolicyName.SCHEMA_COMPATIBILITY_STRATEGY, PolicyOperation.WRITE, authedBiscuit, null));
        assertFalse(authorizationProvider.allowNamespacePolicyOperation(NamespaceName.get(tenant + "/" + namespace), PolicyName.REPLICATION, PolicyOperation.WRITE, authedBiscuit, null));
        assertTrue(authorizationProvider.allowNamespacePolicyOperation(NamespaceName.get(tenant + "/" + namespace), PolicyName.REPLICATION, PolicyOperation.READ, authedBiscuit, null));
        AuthenticationDataSource authData = new AuthenticationDataSource() {
            @Override
            public String getSubscription() {
                return null;
            }
        };

        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.LOOKUP, null));
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/random-ns/" + "test"), authedBiscuit, TopicOperation.LOOKUP, null));
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get("random-tenant/random-ns/test"), authedBiscuit, TopicOperation.CONSUME, authData));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.CONSUME, authData));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.PRODUCE, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + "test123"), authedBiscuit, TopicOperation.CONSUME, authData));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + "test123"), authedBiscuit, TopicOperation.PRODUCE, null));
        assertTrue(authorizationProvider.allowTopicPolicyOperation(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, PolicyName.ALL, PolicyOperation.READ, null));
        assertFalse(authorizationProvider.allowTopicPolicyOperation(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, PolicyName.ALL, PolicyOperation.WRITE, null));

        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderBiscuit.CONF_BISCUIT_PUBLIC_ROOT_KEY, hex(root.public_key().key.getAbyte()));
        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);
        assertFalse(authorizationProvider.isSuperUser(authedBiscuit, null, conf).get());
    }

    @Test
    public void testSuperUser() throws IOException, AuthenticationException, ExecutionException, InterruptedException, Error.SymbolTableOverlap, Error.FormatError, Error.Language, Error.Parser {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit biscuit = Biscuit.make(rng, root, symbols, block0.build());

        String authedBiscuit = authedBiscuit(root, biscuit);
        log.debug(biscuit.print());

        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        Properties properties = new Properties();
        properties.setProperty(AuthenticationProviderBiscuit.CONF_BISCUIT_PUBLIC_ROOT_KEY, hex(root.public_key().key.getAbyte()));
        ServiceConfiguration conf = new ServiceConfiguration();
        conf.setProperties(properties);
        assertTrue(authorizationProvider.isSuperUser(authedBiscuit, null, conf).get());
        assertTrue(authorizationProvider.allowNamespacePolicyOperation(NamespaceName.get("randomTenant/randomNamespace"), PolicyName.REPLICATION, PolicyOperation.WRITE, authedBiscuit, null));
    }

    @Test
    public void testNsLimitationsThenPrefixLimitation() throws IOException, AuthenticationException, ExecutionException, InterruptedException, Error, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit rootBiscuit = Biscuit.make(rng, root, symbols, block0.build());

        // limit on ns tenant/namespace
        Block block1 = rootBiscuit.create_block();
        block1.add_check("check if " + namespaceFact(tenant, namespace) + " or " + topicVariableFact(tenant, namespace));
        Biscuit biscuit = rootBiscuit.attenuate(rng, root, block1.build());

        // limit on tenant/namespace/PREFIX*
        String PREFIX = "INSTANCE_PREFIX_TO_DEFINE";
        Block block2 = biscuit.create_block();
        block2.add_check("check if " + topicVariableFact(tenant, namespace) + ", $topic.starts_with(\"" + PREFIX + "\")");
        biscuit = biscuit.attenuate(rng, root, block2.build());

        String authedBiscuit = authedBiscuit(root, biscuit);

        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();

        log.debug(biscuit.print());
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/test"), authedBiscuit, TopicOperation.PRODUCE, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + PREFIX), authedBiscuit, TopicOperation.PRODUCE, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + PREFIX + "-concat"), authedBiscuit, TopicOperation.PRODUCE, null));
    }

    @Test
    public void testLimitProduceOnTopicStartsWith() throws IOException, AuthenticationException, ExecutionException, InterruptedException, Error, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";

        // root token
        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit rootBiscuit = Biscuit.make(rng, root, symbols, block0.build());

        // limit on tenant/namespace/PREFIX*
        String PREFIX = "PREFIX";
        Block block2 = rootBiscuit.create_block();
        block2.add_check("check if " + topicVariableFact(tenant, namespace) + "," + topicOperationFact(TopicOperation.PRODUCE) + ", $topic.starts_with(\"" + PREFIX + "\")");
        Biscuit biscuit = rootBiscuit.attenuate(rng, root, block2.build());

        String authedBiscuit = authedBiscuit(root, biscuit);

        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();

        log.debug(biscuit.print());
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/test"), authedBiscuit, TopicOperation.PRODUCE, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + PREFIX), authedBiscuit, TopicOperation.PRODUCE, null));
        assertTrue(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + PREFIX + "-concat"), authedBiscuit, TopicOperation.PRODUCE, null));
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/test"), authedBiscuit, TopicOperation.CONSUME, null));
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + PREFIX), authedBiscuit, TopicOperation.CONSUME, null));
        assertFalse(authorizationProvider.allowTopicOperation(TopicName.get(tenant + "/" + namespace + "/" + PREFIX + "-concat"), authedBiscuit, TopicOperation.CONSUME, null));
    }

    @Test
    public void testConsumeOverrideLookup() throws IOException, AuthenticationException, ExecutionException, InterruptedException, Error, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit rootBiscuit = Biscuit.make(rng, root, symbols, block0.build());

        Block block1 = rootBiscuit.create_block();
        block1.add_check("check if " + topicVariableFact(tenant, namespace) + "," + topicOperationFact(TopicOperation.CONSUME));
        Biscuit biscuit = rootBiscuit.attenuate(rng, root, block1.build());

        String authedBiscuit = authedBiscuit(root, biscuit);

        log.debug(biscuit.print());
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        assertTrue(authorizationProvider.allowTopicOperationAsync(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.LOOKUP, null).get());
        assertTrue(authorizationProvider.allowTopicOperationAsync(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.CONSUME, null).get());
    }

    @Test
    public void testProduceOverrideLookup() throws IOException, AuthenticationException, ExecutionException, InterruptedException, Error, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit rootBiscuit = Biscuit.make(rng, root, symbols, block0.build());

        Block block1 = rootBiscuit.create_block();
        block1.add_check("check if " + topicVariableFact(tenant, namespace) + "," + topicOperationFact(TopicOperation.PRODUCE));
        Biscuit biscuit = rootBiscuit.attenuate(rng, root, block1.build());

        String authedBiscuit = authedBiscuit(root, biscuit);

        log.debug(biscuit.print());
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        assertTrue(authorizationProvider.allowTopicOperationAsync(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.LOOKUP, null).get());
        assertTrue(authorizationProvider.allowTopicOperationAsync(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.PRODUCE, null).get());
    }

    @Test
    public void testLookupIsNotOverrodeByProduceOrConsume() throws IOException, AuthenticationException, ExecutionException, InterruptedException, Error, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        SecureRandom rng = new SecureRandom();
        KeyPair root = new KeyPair(rng);
        SymbolTable symbols = Biscuit.default_symbol_table();

        String tenant = "tenantTest";
        String namespace = "namespaceTest";

        Block block0 = new Block(0, symbols);
        block0.add_fact(adminFact);
        Biscuit rootBiscuit = Biscuit.make(rng, root, symbols, block0.build());

        Block block1 = rootBiscuit.create_block();
        block1.add_check("check if " + topicVariableFact(tenant, namespace) + "," + topicOperationFact(TopicOperation.LOOKUP));
        Biscuit biscuit = rootBiscuit.attenuate(rng, root, block1.build());

        String authedBiscuit = authedBiscuit(root, biscuit);

        log.debug(biscuit.print());
        AuthorizationProviderBiscuit authorizationProvider = new AuthorizationProviderBiscuit();
        assertTrue(authorizationProvider.allowTopicOperationAsync(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.LOOKUP, null).get());
        assertFalse(authorizationProvider.allowTopicOperationAsync(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.PRODUCE, null).get());
        assertFalse(authorizationProvider.allowTopicOperationAsync(TopicName.get(tenant + "/" + namespace + "/" + "test"), authedBiscuit, TopicOperation.CONSUME, null).get());
    }
}