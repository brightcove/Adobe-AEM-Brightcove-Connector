<?xml version="1.0" encoding="UTF-8"?>
<jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:granite="http://www.adobe.com/jcr/granite/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
          granite:rel="aem-assets-metadata-form-tab"
          jcr:primaryType="nt:unstructured"
          jcr:title="Brightcove (AUTO)"
          sling:resourceType="granite/ui/components/coral/foundation/container"
          listOrder="2">
    <granite:data
            jcr:primaryType="nt:unstructured"
            tabid="e445e6bf-6bf8-436b-a069-ac0dc7cbda1b"/>
    <items jcr:primaryType="nt:unstructured">
        <col1
                granite:rel="aem-assets-metadata-form-column"
                jcr:primaryType="nt:unstructured"
                sling:resourceType="granite/ui/components/coral/foundation/container"
                listOrder="0">
            <items jcr:primaryType="nt:unstructured">
                <_x0031_498494794917
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/textfield"
                        fieldLabel="Title AUTO"
                        name="./jcr:content/metadata/brc_name"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="text"/>
                </_x0031_498494794917>
                <_x0031_498494825470
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/textfield"
                        fieldLabel="Last Updated"
                        name="./jcr:content/metadata/brc_updated_at"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="text"/>
                </_x0031_498494825470>
                <_x0031_498495036278
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/textfield"
                        fieldLabel="Link to Related Item"
                        name="./jcr:content/metadata/brc_link/url"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="text"/>
                </_x0031_498495036278>
                <_x0031_498495056835
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/textfield"
                        fieldLabel="Text for Related Item"
                        name="./jcr:content/metadata/brc_link/text"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="text"/>
                </_x0031_498495056835>
                <_x0031_498495230730
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/textfield"
                        fieldLabel="Reference ID"
                        name="./jcr:content/metadata/brc_reference_id"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="text"/>
                </_x0031_498495230730>
            </items>
        </col1>
        <col2
                granite:rel="aem-assets-metadata-form-column"
                jcr:primaryType="nt:unstructured"
                sling:resourceType="granite/ui/components/coral/foundation/container"
                listOrder="1">
            <items jcr:primaryType="nt:unstructured">
                <_x0031_498494860451
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/textfield"
                        fieldLabel="Date Published"
                        name="./jcr:content/metadata/brc_created_at"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="text"/>
                </_x0031_498494860451>
                <_x0031_498494879677
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/textfield"
                        fieldLabel="Duration"
                        name="./jcr:content/metadata/brc_duration"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="text"/>
                </_x0031_498494879677>
            </items>
        </col2>
        <col3
                granite:rel="aem-assets-metadata-form-column"
                jcr:primaryType="nt:unstructured"
                sling:resourceType="granite/ui/components/coral/foundation/container"
                listOrder="2">
            <items jcr:primaryType="nt:unstructured">
                <_x0031_498494919689
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/textfield"
                        fieldLabel="Video ID"
                        name="./jcr:content/metadata/brc_id"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="text"/>
                </_x0031_498494919689>
                <_x0031_498494946820
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/textfield"
                        fieldLabel="Short Description"
                        name="./jcr:content/metadata/brc_description"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="text"/>
                </_x0031_498494946820>
                <_x0031_498495107061
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="granite/ui/components/coral/foundation/form/select"
                        fieldLabel="Economics"
                        name="./jcr:content/metadata/brc_economics"
                        renderReadOnly="true">
                    <granite:data
                            jcr:primaryType="nt:unstructured"
                            metaType="dropdown"/>
                    <items jcr:primaryType="nt:unstructured">
                        <_x0031_498495149903
                                jcr:primaryType="nt:unstructured"
                                text="Ad Enabled"
                                value="enabled"/>
                        <_x0031_498495196007
                                jcr:primaryType="nt:unstructured"
                                text="Ad Disabled"
                                value="disabled"/>
                    </items>
                </_x0031_498495107061>
            </items>
        </col3>
    </items>
</jcr:root>