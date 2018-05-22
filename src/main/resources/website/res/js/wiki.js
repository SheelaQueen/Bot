var wikiViewModal = undefined
function pageReady() {
    $("pre code").each((i, block) => {
        hljs.highlightBlock(block)
    })
    $("div.white-text ul").addClass("browser-default")
    wikiViewModal = document.getElementById("wikiViewModal")

    var indexUL = $("#indexUL")
    $(".fillIndexUL").each(function(index) {
        var $this = $(this)
        indexUL.clone().appendTo(this)
    })
    indexUL.remove()

    document.querySelectorAll('.collapsible.wikiCategoryCollapsible').forEach(cur => M.Collapsible.init(cur))
    M.Modal.init(wikiViewModal)
}

function openWikiViewModal() {
    M.Modal.getInstance(wikiViewModal).open()
}

function getHeaderProperties() {
    return {
        breadcrumbs: [
            { name: "GamesROB", path: "/" },
            { name: "Help", path: "/help/" },
            { name: decodeHTML(pageName), path: window.location.href }
        ], color: "#2962FF",
        title: pageName + " - GamesROB Help"
    }
}

function decodeHTML(encodedStr) {
    var parser = new DOMParser, dom = parser.parseFromString(
        '<!doctype html><body>' + encodedStr,
        'text/html'), decodedString = dom.body.textContent

    return decodedString
}