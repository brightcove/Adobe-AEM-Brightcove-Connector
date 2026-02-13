<%@include file="/libs/granite/ui/global.jsp"%><%
%><%@page session="false"
          import="java.util.Arrays,
                  org.apache.commons.lang.StringUtils,
                  com.adobe.granite.workflow.WorkflowException,
                  com.adobe.granite.workflow.WorkflowSession,
                  com.adobe.granite.workflow.metadata.MetaDataMap,
                  com.adobe.granite.workflow.model.WorkflowModel,
                  java.util.ArrayList,
                  java.util.HashMap,
                  java.util.List,
                  org.apache.commons.collections.Transformer,
                  org.apache.commons.collections.iterators.TransformIterator,
                  org.apache.sling.api.resource.ResourceMetadata,
                  org.apache.sling.api.resource.ValueMap,
                  org.apache.sling.api.wrappers.ValueMapDecorator,
                  com.adobe.granite.ui.components.ds.DataSource,
                  com.adobe.granite.ui.components.ds.EmptyDataSource,
                  com.adobe.granite.ui.components.ds.SimpleDataSource,
                  com.adobe.granite.ui.components.ds.ValueMapResource,
                  org.apache.sling.api.resource.ResourceResolver,
                  org.apache.sling.api.resource.Resource,
                  com.adobe.granite.ui.components.Config"%><%

    String exclude = StringUtils.trimToNull(request.getParameter("exclude"));
    Config cfg = cmp.getConfig();

    String workflowTags = StringUtils.join(cfg.get("workflowtags", String[].class), ",");

    try {
        WorkflowSession wfSession = resourceResolver.adaptTo(WorkflowSession.class);
        WorkflowModel[] models = wfSession.getModels();
        List<WorkflowModel> modelsList = new ArrayList<WorkflowModel>();
        for (WorkflowModel model : models) {
            if (shouldBeIncluded(model, workflowTags, false, exclude)) {
                modelsList.add(model);
            }
        }

        final ResourceResolver resolver = resourceResolver;

        DataSource ds;
        if (modelsList.isEmpty()) {
            ds = EmptyDataSource.instance();
        } else {
            ds = new SimpleDataSource(new TransformIterator(modelsList.iterator(), new Transformer() {
                public Object transform(Object input) {
                    try {
                        WorkflowModel model = (WorkflowModel) input;
                        ValueMap vm = new ValueMapDecorator(new HashMap<String, Object>());

                        boolean isMultiResourceSuport = model.getMetaDataMap().get("multiResourceSupport", "false").equals("true");

                        vm.put("value", model.getId());
                        vm.put("text", model.getTitle());

                        ValueMap dataVM = new ValueMapDecorator(new HashMap<String, Object>());
                        dataVM.put("multiResourceSupport", isMultiResourceSuport);

                        // Data sub-node and its properties
                        List<Resource> subChildren = new ArrayList<Resource>();
                        subChildren.add(new ValueMapResource(resolver, model.getId() + "/granite:data", "nt:unstructured", dataVM));
                        ValueMapResource wrappedResource = new ValueMapResource(resolver, model.getId(), "nt:unstructured", vm, subChildren);

                        return wrappedResource;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }));
        }

        request.setAttribute(DataSource.class.getName(), ds);


    } catch (WorkflowException e) {
        log.debug("Not able to fetch workflow models", e);
    }
%>

<%!
    private static boolean shouldBeIncluded(WorkflowModel model, String workflowTags, boolean strict, String exclude) {

        MetaDataMap metaData = model.getMetaDataMap();
        String tagStr = metaData.get("tags", String.class);
        if(tagStr!=null && tagStr.contains(workflowTags)){
			return true;
        }

        return false;
    }
%>
