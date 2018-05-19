function pageReady() {
    var transformAnimate = {
        step: function(curProgress, type) {
            if (type.prop === "transform") $(this).css({ transform: "translate(0, " + curProgress + "px)" }); 
        }
    }

    var homeGameExamplesDiv = $(".homeGameExamplesDiv"), 
        offsetHGE = homeGameExamplesDiv.offset(), 
        xHGE = offsetHGE.left, yHGE = offsetHGE.top, 
        widthHGE = homeGameExamplesDiv.width(), heightHGE = homeGameExamplesDiv.height(), 
        homeGameExample = $(".homeGameExample")
    .on("mouseenter mouseleave click touchend", event => {
        var target = event.currentTarget, $target = $(target), 
            imageRow = $($target.children().get(0)), title = $($target.children().get(1)), description = $($target.children().get(2)),
            imageCol = $(imageRow.children().get(0)), mainImage = $(imageCol.children().get(0)), blurredImage = $(imageCol.children().get(1))

        if (event.type === "mouseenter" || (event.type === "touchend" && !target["data-hover-transformValueAnim"])) {
            var titleTop = title.offset().top,
                descriptionTop = description.offset().top,
                descriptionHeight = description.innerHeight()
                imageRowTop = imageRow.offset().top, 
                imageRowHeight = imageRow.innerHeight(),
                transformValue = (imageRowTop + imageRowHeight / 2 - descriptionTop - descriptionHeight / 2)
            target["data-hover-transformValueAnim"] = transformValue

            new CSSAnimation({ opacity: "0" }, { opacity: "1" }).animate(blurredImage, pow5Out)
            new CSSAnimation({ opacity: "1" }, { opacity: "0" }).animate(mainImage, pow5Out)
            new CSSAnimation({ transform: "translate(0px, 0px)" }, { transform: "translate(0px, " + transformValue + "px)" }, 400).animate(title, pow5Out)
            new CSSAnimation({ opacity: "0", transform: "translate(0px, 0px)" }, { opacity: "1", transform: "translate(0px, " + transformValue + "px)" }, 400).animate(description, pow5Out)
        } else if (event.type === "mouseleave") {
            var transformValueAnim = target["data-hover-transformValueAnim"]
            target["data-hover-transformValueAnim"] = undefined

            new CSSAnimation({ opacity: "1" }, { opacity: "0" }).animate(blurredImage, pow5Out)
            new CSSAnimation({ opacity: "0" }, { opacity: "1" }).animate(mainImage, pow5Out)
            new CSSAnimation({ transform: "translate(0px, " + transformValueAnim + "px)" }, { transform: "translate(0px, 0px)" }, 400).animate(title, pow5In)
            new CSSAnimation({ opacity: "1", transform: "translate(0px, " + transformValueAnim + "px)" }, { opacity: "0", transform: "translate(0px, 0px)" }, 400).animate(description, pow5In)
        } else if ((event.type === "click" && !("ontouchstart" in document.documentElement)) || (event.type === "touchend" && target["data-hover-transformValueAnim"])) loadPage(target.getAttribute("data-help-page"))
        })

    const animationTime = 0.5, delayInc = 100,
          fullDelayTime = homeGameExample.length * (delayInc / 1000)
    homeGameExample.each(function(index) { 
        var delay = index * 60, time = animationTime - (index / homeGameExample.length) * fullDelayTime

        setTimeout(() => {
            $(this).css({ animation: "homeGameExampleAppear " + time + "s ease", opacity: 1 })
        }, delay)
    })
}

function getHeaderProperties() {
    return {
        breadcrumbs: [
            { name: "GamesROB", path: "/" }
        ], color: "#b71c1c",
        title: "GamesROB"
    }
}