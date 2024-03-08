(function ($, $document, author) {
    var self = {},
        assetPath = null,
        CONTENT_FINDER_NAME = 'Brightcove Videos';
    
    // Get Brightcove Videos Asset Path from OSGI Config
    $.getJSON(Granite.HTTP.externalize("/bin/brightcove/getBrightcoveAssetPath.json")).done(function(data){
        assetPath = data["brightcoveAssetPath"];
        
        getBrightcoveAssetsPath(assetPath);
    });
    
    // Get Brightcove Videos 
    function getBrightcoveAssetsPath(brightcoveVideosPath) {

        var brightcoveAssetServlet = '/bin/wcm/contentfinder/asset/view.html',
            itemResourceType = 'cq/gui/components/authoring/assetfinder/asset';
    
        self.loadAssets = function (query, lowerLimit, upperLimit) {
            
            var param = {
                '_dc': new Date().getTime(),
                'query': query.concat("order:\"-jcr:content/jcr:lastModified\" "),
                'mimeType': 'video,application/x-shockwave-flash,application/vnd.rn-realmedia,application/mxf',
                'itemResourceType': itemResourceType,
                'limit': lowerLimit + ".." + upperLimit,
                '_charset_': 'utf-8'
            };
    
            return $.ajax({
                type: 'GET',
                dataType: 'html',
                url: Granite.HTTP.externalize(brightcoveAssetServlet) + brightcoveVideosPath,
                data: param
            });
        };
    
        self.setSearchPath = function (spath) {
            searchPath = spath;
        };
    }
    
    author.ui.assetFinder.register(CONTENT_FINDER_NAME, self);
}(jQuery, jQuery(document), Granite.author));