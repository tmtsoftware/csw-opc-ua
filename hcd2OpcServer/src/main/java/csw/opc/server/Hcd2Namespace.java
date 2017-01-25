/*
 * Copyright (c) 2016 Kevin Herron
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.html.
 */

package csw.opc.server;

import java.lang.reflect.Array;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import csw.opc.server.methods.SqrtMethod;
import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.Reference;
import org.eclipse.milo.opcua.sdk.core.ValueRank;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.AccessContext;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.MethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.api.Namespace;
import org.eclipse.milo.opcua.sdk.server.api.nodes.VariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.AttributeContext;
import org.eclipse.milo.opcua.sdk.server.nodes.ServerNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.AttributeDelegate;
import org.eclipse.milo.opcua.sdk.server.nodes.delegates.AttributeDelegateChain;
import org.eclipse.milo.opcua.sdk.server.util.AnnotationBasedInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.XmlElement;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
import org.eclipse.milo.opcua.stack.core.util.FutureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ulong;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;


/**
 * Based on the ExampleNamespace class from the OPC milo-examples. The filter and disperser
 * items used for this demo were added to the OPC ScalarTypes below.
 */
public class Hcd2Namespace implements Namespace {

  public static final String NAMESPACE_URI = "urn:eclipse:milo:hello-world";

   public static final String NAMESPACE_PREFIX = "HelloWorld/ScalarTypes/";

  public static final String[] FILTERS = new String[]{
    "None",
    "g_G0301",
    "r_G0303",
    "i_G0302",
    "z_G0304",
    "Z_G0322",
    "Y_G0323",
    "u_G0308"
  };

  public static final String[] DISPERSERS = new String[]{
    "Mirror",
    "B1200_G5301",
    "R831_G5302",
    "B600_G5303",
    "B600_G5307",
    "R600_G5304",
    "R400_G5305",
    "R150_G5306"
  };

  private static final Object[][] STATIC_SCALAR_NODES = new Object[][]{
    {"Boolean", Identifiers.Boolean, new Variant(false)},
    {"Byte", Identifiers.Byte, new Variant(ubyte(0x00))},
    {"SByte", Identifiers.SByte, new Variant((byte) 0x00)},
    {"Int16", Identifiers.Int16, new Variant((short) 16)},
    {"Int32", Identifiers.Int32, new Variant(32)},
    {"Int64", Identifiers.Int64, new Variant(64L)},
    {"UInt16", Identifiers.UInt16, new Variant(ushort(16))},
    {"UInt32", Identifiers.UInt32, new Variant(uint(32))},
    {"UInt64", Identifiers.UInt64, new Variant(ulong(64L))},
    {"Float", Identifiers.Float, new Variant(3.14f)},
    {"Double", Identifiers.Double, new Variant(3.14d)},
    {"String", Identifiers.String, new Variant("string value")},
    {"DateTime", Identifiers.DateTime, new Variant(DateTime.now())},
    {"Guid", Identifiers.Guid, new Variant(UUID.randomUUID())},
    {"ByteString", Identifiers.ByteString, new Variant(new ByteString(new byte[]{0x01, 0x02, 0x03, 0x04}))},
    {"XmlElement", Identifiers.XmlElement, new Variant(new XmlElement("<a>hello</a>"))},
    {"LocalizedText", Identifiers.LocalizedText, new Variant(LocalizedText.english("localized text"))},
    {"QualifiedName", Identifiers.QualifiedName, new Variant(new QualifiedName(1234, "defg"))},
    {"NodeId", Identifiers.NodeId, new Variant(new NodeId(1234, "abcd"))},

    {"Duration", Identifiers.Duration, new Variant(1.0)},
    {"UtcTime", Identifiers.UtcTime, new Variant(DateTime.now())},

    {"filter", Identifiers.String, new Variant("None")},
    {"filterPos", Identifiers.Int32, new Variant(0)},
    {"disperser", Identifiers.String, new Variant("Mirror")},
    {"disperserPos", Identifiers.Int32, new Variant(0)}
  };

  private static final Object[][] STATIC_ARRAY_NODES = new Object[][]{
    {"BooleanArray", Identifiers.Boolean, false},
    {"ByteArray", Identifiers.Byte, ubyte(0)},
    {"SByteArray", Identifiers.SByte, (byte) 0x00},
    {"Int16Array", Identifiers.Int16, (short) 16},
    {"Int32Array", Identifiers.Int32, 32},
    {"Int64Array", Identifiers.Int64, 64L},
    {"UInt16Array", Identifiers.UInt16, ushort(16)},
    {"UInt32Array", Identifiers.UInt32, uint(32)},
    {"UInt64Array", Identifiers.UInt64, ulong(64L)},
    {"FloatArray", Identifiers.Float, 3.14f},
    {"DoubleArray", Identifiers.Double, 3.14d},
    {"StringArray", Identifiers.String, "string value"},
    {"DateTimeArray", Identifiers.DateTime, new Variant(DateTime.now())},
    {"GuidArray", Identifiers.Guid, new Variant(UUID.randomUUID())},
    {"ByteStringArray", Identifiers.ByteString, new Variant(new ByteString(new byte[]{0x01, 0x02, 0x03, 0x04}))},
    {"XmlElementArray", Identifiers.XmlElement, new Variant(new XmlElement("<a>hello</a>"))},
    {"LocalizedTextArray", Identifiers.LocalizedText, new Variant(LocalizedText.english("localized text"))},
    {"QualifiedNameArray", Identifiers.QualifiedName, new Variant(new QualifiedName(1234, "defg"))},
    {"NodeIdArray", Identifiers.NodeId, new Variant(new NodeId(1234, "abcd"))}
  };


  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final Random random = new Random();

  private final SubscriptionModel subscriptionModel;

  private final OpcUaServer server;
  private final UShort namespaceIndex;

  private  NodeId filterNodeId;
  private  NodeId filterPosNodeId;
  private  NodeId disperserNodeId;
  private  NodeId disperserPosNodeId;


  public Hcd2Namespace(OpcUaServer server, UShort namespaceIndex) {
    this.server = server;
    this.namespaceIndex = namespaceIndex;

    subscriptionModel = new SubscriptionModel(server, this);

    try {
      // Create a "HelloWorld" folder and add it to the node manager
      NodeId folderNodeId = new NodeId(namespaceIndex, "HelloWorld");

      UaFolderNode folderNode = new UaFolderNode(
        server.getNodeMap(),
        folderNodeId,
        new QualifiedName(namespaceIndex, "HelloWorld"),
        LocalizedText.english("HelloWorld")
      );

      server.getNodeMap().addNode(folderNode);

      // Make sure our new folder shows up under the server's Objects folder
      server.getUaNamespace().addReference(
        Identifiers.ObjectsFolder,
        Identifiers.Organizes,
        true,
        folderNodeId.expanded(),
        NodeClass.Object
      );

      // Add the rest of the nodes
      addVariableNodes(folderNode);

      addMethodNode(folderNode);

      filterNodeId = new NodeId(namespaceIndex, NAMESPACE_PREFIX + "filter");
      filterPosNodeId = new NodeId(namespaceIndex, NAMESPACE_PREFIX + "filterPos");
      disperserNodeId = new NodeId(namespaceIndex, NAMESPACE_PREFIX + "disperser");
      disperserPosNodeId = new NodeId(namespaceIndex, NAMESPACE_PREFIX + "disperserPos");

    } catch (UaException e) {
      logger.error("Error adding nodes: {}", e.getMessage(), e);
    }
  }

  @Override
  public UShort getNamespaceIndex() {
    return namespaceIndex;
  }

  @Override
  public String getNamespaceUri() {
    return NAMESPACE_URI;
  }

  private void addVariableNodes(UaFolderNode rootNode) {
    addArrayNodes(rootNode);
    addScalarNodes(rootNode);
    addAdminReadableNodes(rootNode);
    addAdminWritableNodes(rootNode);
    addDynamicNodes(rootNode);
  }

  private void addArrayNodes(UaFolderNode rootNode) {
    UaFolderNode arrayTypesFolder = new UaFolderNode(
      server.getNodeMap(),
      new NodeId(namespaceIndex, "HelloWorld/ArrayTypes"),
      new QualifiedName(namespaceIndex, "ArrayTypes"),
      LocalizedText.english("ArrayTypes")
    );

    server.getNodeMap().addNode(arrayTypesFolder);
    rootNode.addOrganizes(arrayTypesFolder);

    for (Object[] os : STATIC_ARRAY_NODES) {
      String name = (String) os[0];
      NodeId typeId = (NodeId) os[1];
      Object value = os[2];
      Object array = Array.newInstance(value.getClass(), 4);
      for (int i = 0; i < 4; i++) {
        Array.set(array, i, value);
      }
      Variant variant = new Variant(array);

      UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(server.getNodeMap())
        .setNodeId(new NodeId(namespaceIndex, "HelloWorld/ArrayTypes/" + name))
        .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
        .setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
        .setBrowseName(new QualifiedName(namespaceIndex, name))
        .setDisplayName(LocalizedText.english(name))
        .setDataType(typeId)
        .setTypeDefinition(Identifiers.BaseDataVariableType)
        .setValueRank(ValueRank.OneDimension.getValue())
        .setArrayDimensions(new UInteger[]{uint(0)})
        .build();

      node.setValue(new DataValue(variant));

      node.setAttributeDelegate(new ValueLoggingDelegate());

      server.getNodeMap().addNode(node);
      arrayTypesFolder.addOrganizes(node);
    }
  }

  private void addScalarNodes(UaFolderNode rootNode) {
    UaFolderNode scalarTypesFolder = new UaFolderNode(
      server.getNodeMap(),
      new NodeId(namespaceIndex, "HelloWorld/ScalarTypes"),
      new QualifiedName(namespaceIndex, "ScalarTypes"),
      LocalizedText.english("ScalarTypes")
    );

    server.getNodeMap().addNode(scalarTypesFolder);
    rootNode.addOrganizes(scalarTypesFolder);

    for (Object[] os : STATIC_SCALAR_NODES) {
      String name = (String) os[0];
      NodeId typeId = (NodeId) os[1];
      Variant variant = (Variant) os[2];

      UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(server.getNodeMap())
        .setNodeId(new NodeId(namespaceIndex, "HelloWorld/ScalarTypes/" + name))
        .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
        .setUserAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
        .setBrowseName(new QualifiedName(namespaceIndex, name))
        .setDisplayName(LocalizedText.english(name))
        .setDataType(typeId)
        .setTypeDefinition(Identifiers.BaseDataVariableType)
        .build();

      node.setValue(new DataValue(variant));

      node.setAttributeDelegate(new ValueLoggingDelegate());

      server.getNodeMap().addNode(node);
      scalarTypesFolder.addOrganizes(node);
    }
  }

  private void addAdminReadableNodes(UaFolderNode rootNode) {
    UaFolderNode adminFolder = new UaFolderNode(
      server.getNodeMap(),
      new NodeId(namespaceIndex, "HelloWorld/OnlyAdminCanRead"),
      new QualifiedName(namespaceIndex, "OnlyAdminCanRead"),
      LocalizedText.english("OnlyAdminCanRead")
    );

    server.getNodeMap().addNode(adminFolder);
    rootNode.addOrganizes(adminFolder);

    String name = "String";
    UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(server.getNodeMap())
      .setNodeId(new NodeId(namespaceIndex, "HelloWorld/OnlyAdminCanRead/" + name))
      .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
      .setBrowseName(new QualifiedName(namespaceIndex, name))
      .setDisplayName(LocalizedText.english(name))
      .setDataType(Identifiers.String)
      .setTypeDefinition(Identifiers.BaseDataVariableType)
      .build();

    node.setValue(new DataValue(new Variant("shh... don't tell the lusers")));

    node.setAttributeDelegate(new RestrictedAccessDelegate(identity -> {
      if ("admin".equals(identity)) {
        return AccessLevel.READ_WRITE;
      } else {
        return AccessLevel.NONE;
      }
    }));

    server.getNodeMap().addNode(node);
    adminFolder.addOrganizes(node);
  }

  private void addAdminWritableNodes(UaFolderNode rootNode) {
    UaFolderNode adminFolder = new UaFolderNode(
      server.getNodeMap(),
      new NodeId(namespaceIndex, "HelloWorld/OnlyAdminCanWrite"),
      new QualifiedName(namespaceIndex, "OnlyAdminCanWrite"),
      LocalizedText.english("OnlyAdminCanWrite")
    );

    server.getNodeMap().addNode(adminFolder);
    rootNode.addOrganizes(adminFolder);

    String name = "String";
    UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(server.getNodeMap())
      .setNodeId(new NodeId(namespaceIndex, "HelloWorld/OnlyAdminCanWrite/" + name))
      .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
      .setBrowseName(new QualifiedName(namespaceIndex, name))
      .setDisplayName(LocalizedText.english(name))
      .setDataType(Identifiers.String)
      .setTypeDefinition(Identifiers.BaseDataVariableType)
      .build();

    node.setValue(new DataValue(new Variant("admin was here")));

    node.setAttributeDelegate(new RestrictedAccessDelegate(identity -> {
      if ("admin".equals(identity)) {
        return AccessLevel.READ_WRITE;
      } else {
        return AccessLevel.READ_ONLY;
      }
    }));

    server.getNodeMap().addNode(node);
    adminFolder.addOrganizes(node);
  }

  private void addDynamicNodes(UaFolderNode rootNode) {
    UaFolderNode dynamicFolder = new UaFolderNode(
      server.getNodeMap(),
      new NodeId(namespaceIndex, "HelloWorld/Dynamic"),
      new QualifiedName(namespaceIndex, "Dynamic"),
      LocalizedText.english("Dynamic")
    );

    server.getNodeMap().addNode(dynamicFolder);
    rootNode.addOrganizes(dynamicFolder);

    // Dynamic Boolean
    {
      String name = "Boolean";
      NodeId typeId = Identifiers.Boolean;
      Variant variant = new Variant(false);

      UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(server.getNodeMap())
        .setNodeId(new NodeId(namespaceIndex, "HelloWorld/Dynamic/" + name))
        .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
        .setBrowseName(new QualifiedName(namespaceIndex, name))
        .setDisplayName(LocalizedText.english(name))
        .setDataType(typeId)
        .setTypeDefinition(Identifiers.BaseDataVariableType)
        .build();

      node.setValue(new DataValue(variant));

      AttributeDelegate delegate = AttributeDelegateChain.create(
        new AttributeDelegate() {
          @Override
          public DataValue getValue(AttributeContext context, VariableNode node) throws UaException {
            return new DataValue(new Variant(random.nextBoolean()));
          }
        },
        ValueLoggingDelegate::new
      );

      node.setAttributeDelegate(delegate);

      server.getNodeMap().addNode(node);
      dynamicFolder.addOrganizes(node);
    }

    // Dynamic Int32
    {
      String name = "Int32";
      NodeId typeId = Identifiers.Int32;
      Variant variant = new Variant(0);

      UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(server.getNodeMap())
        .setNodeId(new NodeId(namespaceIndex, "HelloWorld/Dynamic/" + name))
        .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
        .setBrowseName(new QualifiedName(namespaceIndex, name))
        .setDisplayName(LocalizedText.english(name))
        .setDataType(typeId)
        .setTypeDefinition(Identifiers.BaseDataVariableType)
        .build();

      node.setValue(new DataValue(variant));

      AttributeDelegate delegate = AttributeDelegateChain.create(
        new AttributeDelegate() {
          @Override
          public DataValue getValue(AttributeContext context, VariableNode node) throws UaException {
            return new DataValue(new Variant(random.nextInt()));
          }
        },
        ValueLoggingDelegate::new
      );

      node.setAttributeDelegate(delegate);

      server.getNodeMap().addNode(node);
      dynamicFolder.addOrganizes(node);
    }

    // Dynamic Double
    {
      String name = "Double";
      NodeId typeId = Identifiers.Double;
      Variant variant = new Variant(0.0);

      UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(server.getNodeMap())
        .setNodeId(new NodeId(namespaceIndex, "HelloWorld/Dynamic/" + name))
        .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
        .setBrowseName(new QualifiedName(namespaceIndex, name))
        .setDisplayName(LocalizedText.english(name))
        .setDataType(typeId)
        .setTypeDefinition(Identifiers.BaseDataVariableType)
        .build();

      node.setValue(new DataValue(variant));

      AttributeDelegate delegate = AttributeDelegateChain.create(
        new AttributeDelegate() {
          @Override
          public DataValue getValue(AttributeContext context, VariableNode node) throws UaException {
            return new DataValue(new Variant(random.nextDouble()));
          }
        },
        ValueLoggingDelegate::new
      );

      node.setAttributeDelegate(delegate);

      server.getNodeMap().addNode(node);
      dynamicFolder.addOrganizes(node);
    }
  }

  private void addMethodNode(UaFolderNode folderNode) {
    UaMethodNode methodNode = UaMethodNode.builder(server.getNodeMap())
      .setNodeId(new NodeId(namespaceIndex, "HelloWorld/sqrt(x)"))
      .setBrowseName(new QualifiedName(namespaceIndex, "sqrt(x)"))
      .setDisplayName(new LocalizedText(null, "sqrt(x)"))
      .setDescription(
        LocalizedText.english("Returns the correctly rounded positive square root of a double value."))
      .build();


    try {
      AnnotationBasedInvocationHandler invocationHandler =
        AnnotationBasedInvocationHandler.fromAnnotatedObject(
          server.getNodeMap(), new SqrtMethod());

      methodNode.setProperty(UaMethodNode.InputArguments, invocationHandler.getInputArguments());
      methodNode.setProperty(UaMethodNode.OutputArguments, invocationHandler.getOutputArguments());
      methodNode.setInvocationHandler(invocationHandler);

      server.getNodeMap().addNode(methodNode);

      folderNode.addReference(new Reference(
        folderNode.getNodeId(),
        Identifiers.HasComponent,
        methodNode.getNodeId().expanded(),
        methodNode.getNodeClass(),
        true
      ));

      methodNode.addReference(new Reference(
        methodNode.getNodeId(),
        Identifiers.HasComponent,
        folderNode.getNodeId().expanded(),
        folderNode.getNodeClass(),
        false
      ));
    } catch (Exception e) {
      logger.error("Error creating sqrt() method.", e);
    }
  }

  @Override
  public CompletableFuture<List<Reference>> browse(AccessContext context, NodeId nodeId) {
    ServerNode node = server.getNodeMap().get(nodeId);

    if (node != null) {
      return CompletableFuture.completedFuture(node.getReferences());
    } else {
      return FutureUtils.failedFuture(new UaException(StatusCodes.Bad_NodeIdUnknown));
    }
  }

  @Override
  public void read(
    ReadContext context,
    Double maxAge,
    TimestampsToReturn timestamps,
    List<ReadValueId> readValueIds) {

    List<DataValue> results = Lists.newArrayListWithCapacity(readValueIds.size());

    for (ReadValueId readValueId : readValueIds) {
      ServerNode node = server.getNodeMap().get(readValueId.getNodeId());

      if (node != null) {
        DataValue value = node.readAttribute(
          new AttributeContext(context),
          readValueId.getAttributeId(),
          timestamps,
          readValueId.getIndexRange()
        );

        results.add(value);
      } else {
        results.add(new DataValue(StatusCodes.Bad_NodeIdUnknown));
      }
    }

    context.complete(results);
  }

  @Override
  public void write(WriteContext context, List<WriteValue> writeValues) {
    List<StatusCode> results = Lists.newArrayListWithCapacity(writeValues.size());

    for (WriteValue writeValue : writeValues) {
      ServerNode node = server.getNodeMap().get(writeValue.getNodeId());

      if (node != null) {
        try {
          node.writeAttribute(
            new AttributeContext(context),
            writeValue.getAttributeId(),
            writeValue.getValue(),
            writeValue.getIndexRange()
          );

          // React when the filter or disperser value is set to simulate wheel moving slowly
          if (writeValue.getNodeId().equals(filterNodeId)) {
            incFilterPos();
          } else if (writeValue.getNodeId().equals(disperserNodeId)) {
            incDisperserPos();
          }

          results.add(StatusCode.GOOD);

          logger.info(
            "Wrote value {} to {} attribute of {}",
            writeValue.getValue().getValue(),
            AttributeId.from(writeValue.getAttributeId()).map(Object::toString).orElse("unknown"),
            node.getNodeId());
        } catch (UaException e) {
          logger.error("Unable to write value={}", writeValue.getValue(), e);
          results.add(e.getStatusCode());
        }
      } else {
        results.add(new StatusCode(StatusCodes.Bad_NodeIdUnknown));
      }
    }

    context.complete(results);
  }

  private void incFilterPos() {
    UaVariableNode filterNode = (UaVariableNode) server.getNodeMap().get(filterNodeId);
    UaVariableNode filterPosNode = (UaVariableNode) server.getNodeMap().get(filterPosNodeId);
    String filter = (String) filterNode.getValue().getValue().getValue();
    int filterPos = (Integer) filterPosNode.getValue().getValue().getValue();
    if (!FILTERS[filterPos].equals(filter)) {
      filterPos = (filterPos + 1) % FILTERS.length;
      logger.info("Setting filterPos  to = " + filterPos);
      filterPosNode.setValue(new DataValue(new Variant(filterPos)));
      if (!FILTERS[filterPos].equals(filter)) {
        server.getScheduledExecutorService().schedule(this::incFilterPos, 1000L, TimeUnit.MILLISECONDS);
      }
    }
  }

  private void incDisperserPos() {
    UaVariableNode disperserNode = (UaVariableNode) server.getNodeMap().get(disperserNodeId);
    UaVariableNode disperserPosNode = (UaVariableNode) server.getNodeMap().get(disperserPosNodeId);
    String disperser = (String) disperserNode.getValue().getValue().getValue();
    int disperserPos = (Integer) disperserPosNode.getValue().getValue().getValue();
    if (!DISPERSERS[disperserPos].equals(disperser)) {
      disperserPos = (disperserPos + 1) % DISPERSERS.length;
      logger.info("Setting disperserPos  to = " + disperserPos);
      disperserPosNode.setValue(new DataValue(new Variant(disperserPos)));
      if (!DISPERSERS[disperserPos].equals(disperser)) {
        server.getScheduledExecutorService().schedule(this::incDisperserPos, 1000L, TimeUnit.MILLISECONDS);
      }
    }
  }


  @Override
  public void onDataItemsCreated(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsCreated(dataItems);
  }

  @Override
  public void onDataItemsModified(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsModified(dataItems);
  }

  @Override
  public void onDataItemsDeleted(List<DataItem> dataItems) {
    subscriptionModel.onDataItemsDeleted(dataItems);
  }

  @Override
  public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
    subscriptionModel.onMonitoringModeChanged(monitoredItems);
  }

  @Override
  public Optional<MethodInvocationHandler> getInvocationHandler(NodeId methodId) {
    Optional<ServerNode> node = server.getNodeMap().getNode(methodId);

    return node.flatMap(n -> {
      if (n instanceof UaMethodNode) {
        return ((UaMethodNode) n).getInvocationHandler();
      } else {
        return Optional.empty();
      }
    });
  }

}






//package csw.opc.server;
//
///*
// * Modified from org.eclipse.milo.opcua.server.ctt.CttNamespace for test.
// */
//
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.CompletableFuture;
//import java.util.concurrent.TimeUnit;
//
//import com.google.common.collect.PeekingIterator;
//import org.eclipse.milo.opcua.sdk.core.AccessLevel;
//import org.eclipse.milo.opcua.sdk.core.Reference;
//import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
//import org.eclipse.milo.opcua.sdk.server.api.*;
//import org.eclipse.milo.opcua.sdk.server.nodes.*;
//import org.eclipse.milo.opcua.sdk.server.util.SubscriptionModel;
//import org.eclipse.milo.opcua.stack.core.AttributeId;
//import org.eclipse.milo.opcua.stack.core.Identifiers;
//import org.eclipse.milo.opcua.stack.core.StatusCodes;
//import org.eclipse.milo.opcua.stack.core.UaException;
//import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
//import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
//import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
//import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
//import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
//import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
//import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
//import org.eclipse.milo.opcua.stack.core.types.enumerated.NodeClass;
//import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
//import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
//import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;
//import com.google.common.collect.Iterators;
//import com.google.common.collect.Lists;
//import com.google.common.collect.Maps;
//import org.eclipse.milo.opcua.stack.core.util.FutureUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;
//
//@SuppressWarnings("WeakerAccess")
//public class Hcd2Namespace implements Namespace {
//
//  public static final String NAMESPACE_URI = "urn:csw:opcua:hcd2-namespace";
//
//  public static final String NAMESPACE_PREFIX = "/Static/AllProfiles/Scalar/";
//
//  public static final String[] FILTERS = new String[]{
//    "None",
//    "g_G0301",
//    "r_G0303",
//    "i_G0302",
//    "z_G0304",
//    "Z_G0322",
//    "Y_G0323",
//    "u_G0308"
//  };
//
//  public static final String[] DISPERSERS = new String[]{
//    "Mirror",
//    "B1200_G5301",
//    "R831_G5302",
//    "B600_G5303",
//    "B600_G5307",
//    "R600_G5304",
//    "R400_G5305",
//    "R150_G5306"
//  };
//
//
//  // --
//
//  private final Logger logger = LoggerFactory.getLogger(getClass());
//
//  private final Map<NodeId, UaNode> nodes = Maps.newConcurrentMap();
//
//  private final UaFolderNode folderNode;
//  private final SubscriptionModel subscriptionModel;
//
//  private final OpcUaServer server;
//  private final UShort namespaceIndex;
//
//  private final NodeId filterNodeId;
//  private final NodeId filterPosNodeId;
//  private final NodeId disperserNodeId;
//  private final NodeId disperserPosNodeId;
//
//
//  // --
//
//  public Hcd2Namespace(OpcUaServer server, UShort namespaceIndex) {
//    this.server = server;
//    this.namespaceIndex = namespaceIndex;
//
//    NodeId folderNodeId = new NodeId(namespaceIndex, "HCD2");
//
//    folderNode = new UaFolderNode(
//      server.getNodeMap(),
//      folderNodeId,
//      new QualifiedName(namespaceIndex, "HCD2"),
//      LocalizedText.english("HCD2"));
//
//    nodes.put(folderNodeId, folderNode);
//
//    try {
//      server.getUaNamespace().addReference(
//        Identifiers.ObjectsFolder,
//        Identifiers.Organizes,
//        true,
//        folderNodeId.expanded(),
//        NodeClass.Object);
//    } catch (UaException e) {
//      logger.error("Error adding reference to Connections folder.", e);
//    }
//
//    subscriptionModel = new SubscriptionModel(server, this);
//
//    addStaticScalarNodes();
//
//    filterNodeId = new NodeId(namespaceIndex, NAMESPACE_PREFIX + "filter");
//    filterPosNodeId = new NodeId(namespaceIndex, NAMESPACE_PREFIX + "filterPos");
//    disperserNodeId = new NodeId(namespaceIndex, NAMESPACE_PREFIX + "disperser");
//    disperserPosNodeId = new NodeId(namespaceIndex, NAMESPACE_PREFIX + "disperserPos");
//  }
//
//  // For testing, the filter and disperser are the main variables that
//  // are set and read by the clients. The filterPos and dispeserPos variables
//  // are supposed to simulate variables that give the current positions as
//  // intermediate values while the device is moving to the demand position.
//  private static final Object[][] STATIC_SCALAR_NODES = new Object[][]{
//    {"filter", Identifiers.String, new Variant("None")},
//    {"filterPos", Identifiers.Int32, new Variant(0)},
//    {"disperser", Identifiers.String, new Variant("Mirror")},
//    {"disperserPos", Identifiers.Int32, new Variant(0)}
//  };
//
//  private void addStaticScalarNodes() {
//    UaObjectNode folder = addFoldersToRoot(folderNode, NAMESPACE_PREFIX);
//
//    for (Object[] os : STATIC_SCALAR_NODES) {
//      String name = (String) os[0];
//      NodeId typeId = (NodeId) os[1];
//      Variant variant = (Variant) os[2];
//
//      UaVariableNode node = new UaVariableNode.UaVariableNodeBuilder(server.getNodeMap())
//        .setNodeId(new NodeId(namespaceIndex, NAMESPACE_PREFIX + name))
//        .setAccessLevel(ubyte(AccessLevel.getMask(AccessLevel.READ_WRITE)))
//        .setBrowseName(new QualifiedName(namespaceIndex, name))
//        .setDisplayName(LocalizedText.english(name))
//        .setDataType(typeId)
//        .setTypeDefinition(Identifiers.BaseDataVariableType)
//        .build();
//
//      node.setValue(new DataValue(variant));
//
//      folder.addReference(new Reference(
//        folder.getNodeId(),
//        Identifiers.Organizes,
//        node.getNodeId().expanded(),
//        node.getNodeClass(),
//        true
//      ));
//
//      logger.debug("Added reference: {} -> {}", folder.getNodeId(), node.getNodeId());
//
//      nodes.put(node.getNodeId(), node);
//    }
//  }
//
//  private UaObjectNode addFoldersToRoot(UaNode root, String path) {
//    if (path.startsWith("/")) path = path.substring(1, path.length());
//    String[] elements = path.split("/");
//
//    LinkedList<UaObjectNode> folderNodes = processPathElements(
//      Lists.newArrayList(elements),
//      Lists.newArrayList(),
//      Lists.newLinkedList()
//    );
//
//    UaObjectNode firstNode = folderNodes.getFirst();
//
//    if (!nodes.containsKey(firstNode.getNodeId())) {
//      nodes.put(firstNode.getNodeId(), firstNode);
//
//      nodes.get(root.getNodeId()).addReference(new Reference(
//        root.getNodeId(),
//        Identifiers.Organizes,
//        firstNode.getNodeId().expanded(),
//        firstNode.getNodeClass(),
//        true
//      ));
//
//      logger.debug("Added reference: {} -> {}", root.getNodeId(), firstNode.getNodeId());
//    }
//
//    PeekingIterator<UaObjectNode> iterator = Iterators.peekingIterator(folderNodes.iterator());
//
//    while (iterator.hasNext()) {
//      UaObjectNode node = iterator.next();
//
//      nodes.putIfAbsent(node.getNodeId(), node);
//
//      if (iterator.hasNext()) {
//        UaObjectNode next = iterator.peek();
//
//        if (!nodes.containsKey(next.getNodeId())) {
//          nodes.put(next.getNodeId(), next);
//
//          nodes.get(node.getNodeId()).addReference(new Reference(
//            node.getNodeId(),
//            Identifiers.Organizes,
//            next.getNodeId().expanded(),
//            next.getNodeClass(),
//            true
//          ));
//
//          logger.debug("Added reference: {} -> {}", node.getNodeId(), next.getNodeId());
//        }
//      }
//    }
//
//    return folderNodes.getLast();
//  }
//
//  private LinkedList<UaObjectNode> processPathElements(List<String> elements, List<String> path, LinkedList<UaObjectNode> nodes) {
//    if (elements.size() == 1) {
//      String name = elements.get(0);
//      String prefix = String.join("/", path) + "/";
//      if (!prefix.startsWith("/")) prefix = "/" + prefix;
//
//      UaObjectNode node = UaObjectNode.builder(server.getNodeMap())
//        .setNodeId(new NodeId(namespaceIndex, prefix + name))
//        .setBrowseName(new QualifiedName(namespaceIndex, name))
//        .setDisplayName(LocalizedText.english(name))
//        .setTypeDefinition(Identifiers.FolderType)
//        .build();
//
//      nodes.add(node);
//
//      return nodes;
//    } else {
//      String name = elements.get(0);
//      String prefix = String.join("/", path) + "/";
//      if (!prefix.startsWith("/")) prefix = "/" + prefix;
//
//      UaObjectNode node = UaObjectNode.builder(server.getNodeMap())
//        .setNodeId(new NodeId(namespaceIndex, prefix + name))
//        .setBrowseName(new QualifiedName(namespaceIndex, name))
//        .setDisplayName(LocalizedText.english(name))
//        .setTypeDefinition(Identifiers.FolderType)
//        .build();
//
//      nodes.add(node);
//      path.add(name);
//
//      return processPathElements(elements.subList(1, elements.size()), path, nodes);
//    }
//  }
//
//  @Override
//  public UShort getNamespaceIndex() {
//    return namespaceIndex;
//  }
//
//  @Override
//  public String getNamespaceUri() {
//    return NAMESPACE_URI;
//  }
//
//
//  @Override
//  public CompletableFuture<List<Reference>> browse(AccessContext context, NodeId nodeId) {
//    ServerNode node = server.getNodeMap().get(nodeId);
//
//    if (node != null) {
//      return CompletableFuture.completedFuture(node.getReferences());
//    } else {
//      return FutureUtils.failedFuture(new UaException(StatusCodes.Bad_NodeIdUnknown));
//    }
//  }
//
//  @Override
//  public void read(
//    ReadContext context,
//    Double maxAge,
//    TimestampsToReturn timestamps,
//    List<ReadValueId> readValueIds) {
//
//    List<DataValue> results = Lists.newArrayListWithCapacity(readValueIds.size());
//
//    for (ReadValueId readValueId : readValueIds) {
//      ServerNode node = server.getNodeMap().get(readValueId.getNodeId());
//
//      if (node != null) {
//        DataValue value = node.readAttribute(
//          new AttributeContext(context),
//          readValueId.getAttributeId(),
//          timestamps,
//          readValueId.getIndexRange()
//        );
//
//        results.add(value);
//      } else {
//        results.add(new DataValue(StatusCodes.Bad_NodeIdUnknown));
//      }
//    }
//
//    context.complete(results);
//  }
//
//  @Override
//  public void write(WriteContext context, List<WriteValue> writeValues) {
//    List<StatusCode> results = Lists.newArrayListWithCapacity(writeValues.size());
//
//    for (WriteValue writeValue : writeValues) {
//      ServerNode node = server.getNodeMap().get(writeValue.getNodeId());
//
//      if (node != null) {
//        try {
//          node.writeAttribute(
//            new AttributeContext(context),
//            writeValue.getAttributeId(),
//            writeValue.getValue(),
//            writeValue.getIndexRange()
//          );
//
//          // React when the filter or disperser value is set to simulate wheel moving slowly
//          if (writeValue.getNodeId().equals(filterNodeId)) {
//            incFilterPos();
//          } else if (writeValue.getNodeId().equals(disperserNodeId)) {
//            incDisperserPos();
//          }
//
//          results.add(StatusCode.GOOD);
//
//          logger.info(
//            "Wrote value {} to {} attribute of {}",
//            writeValue.getValue().getValue(),
//            AttributeId.from(writeValue.getAttributeId()).map(Object::toString).orElse("unknown"),
//            node.getNodeId());
//        } catch (UaException e) {
//          logger.error("Unable to write value={}", writeValue.getValue(), e);
//          results.add(e.getStatusCode());
//        }
//      } else {
//        results.add(new StatusCode(StatusCodes.Bad_NodeIdUnknown));
//      }
//    }
//
//    context.complete(results);
//  }
//
//  private void incFilterPos() {
//    UaVariableNode filterNode = (UaVariableNode) nodes.get(filterNodeId);
//    UaVariableNode filterPosNode = (UaVariableNode) nodes.get(filterPosNodeId);
//    String filter = (String) filterNode.getValue().getValue().getValue();
//    int filterPos = (Integer) filterPosNode.getValue().getValue().getValue();
//    if (!FILTERS[filterPos].equals(filter)) {
//      filterPos = (filterPos + 1) % FILTERS.length;
//      logger.info("Setting filterPos  to = " + filterPos);
//      filterPosNode.setValue(new DataValue(new Variant(filterPos)));
//      if (!FILTERS[filterPos].equals(filter)) {
//        server.getScheduledExecutorService().schedule(this::incFilterPos, 1000L, TimeUnit.MILLISECONDS);
//      }
//    }
//  }
//
//  private void incDisperserPos() {
//    UaVariableNode disperserNode = (UaVariableNode) nodes.get(disperserNodeId);
//    UaVariableNode disperserPosNode = (UaVariableNode) nodes.get(disperserPosNodeId);
//    String disperser = (String) disperserNode.getValue().getValue().getValue();
//    int disperserPos = (Integer) disperserPosNode.getValue().getValue().getValue();
//    if (!DISPERSERS[disperserPos].equals(disperser)) {
//      disperserPos = (disperserPos + 1) % DISPERSERS.length;
//      logger.info("Setting disperserPos  to = " + disperserPos);
//      disperserPosNode.setValue(new DataValue(new Variant(disperserPos)));
//      if (!DISPERSERS[disperserPos].equals(disperser)) {
//        server.getScheduledExecutorService().schedule(this::incDisperserPos, 1000L, TimeUnit.MILLISECONDS);
//      }
//    }
//  }
//
//
//  @Override
//  public void onDataItemsCreated(List<DataItem> dataItems) {
//    subscriptionModel.onDataItemsCreated(dataItems);
//  }
//
//  @Override
//  public void onDataItemsModified(List<DataItem> dataItems) {
//    subscriptionModel.onDataItemsModified(dataItems);
//  }
//
//  @Override
//  public void onDataItemsDeleted(List<DataItem> dataItems) {
//    subscriptionModel.onDataItemsDeleted(dataItems);
//  }
//
//  @Override
//  public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {
//    subscriptionModel.onMonitoringModeChanged(monitoredItems);
//  }
//
//  @Override
//  public Optional<MethodInvocationHandler> getInvocationHandler(NodeId methodId) {
//    Optional<ServerNode> node = server.getNodeMap().getNode(methodId);
//
//    return node.flatMap(n -> {
//      if (n instanceof UaMethodNode) {
//        return ((UaMethodNode) n).getInvocationHandler();
//      } else {
//        return Optional.empty();
//      }
//    });
//  }
//
//}
