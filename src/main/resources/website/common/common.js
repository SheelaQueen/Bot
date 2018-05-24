var stopBckAnim = false, $document = $(document), breadcrumbsWrapper = undefined, headerColor = undefined, title = undefined, $window = undefined, footer = undefined, loadSpinnerWrapper = undefined
const spinnerElements = 6
$document.ready(() => {
    var $nav = $("nav")
    breadcrumbsWrapper = $(".breadcrumbsWrapper")
    title = $("title")

    var hash = window.location.hash
    if (hash.length > 0) {
        var hashValuesObject = {}
        hash.substring(1).split("&").forEach(cur => {
            var indexOfEquals = cur.indexOf("="), key = cur.substring(0, indexOfEquals), value = cur.substring(indexOfEquals + 1).replace("%20", " ").replace("+", " ").replace("<", "&lt;").replace(">", "&rt;")
            hashValuesObject[key] = value
        })
        $.each(hashValuesObject, (key, value) => {
            if (hashValuesHandlers.hasOwnProperty(key)) hashValuesHandlers[key](value, hashValuesObject)
        })
    }

    if (typeof pageReady === "function") pageReady()
    loadHeaderProperties()

    var top = true
    $document.scroll(() => {
        if ($document.scrollTop() == 0 && !top) {
            $nav.addClass("hideNav")
            top = true
        } else if ($document.scrollTop() != 0 && top) {
            $nav.removeClass("hideNav").css({ "background-color": headerColor })
            top = false
        }
    })
    
    M.Sidenav.init(document.querySelectorAll(".sidenav"))
    M.Collapsible.init(document.querySelectorAll(".collapsible"))
    M.Dropdown.init(document.querySelectorAll(".dropdown-trigger"))

    $window = $("window")
    footer = $("footer")
    loadSpinnerWrapper = $(".loadSpinnerWrapper")
    if (window.innerWidth < 1200) breadcrumbsWrapper.addClass("invisible")
    $window.on("resize", () => {
        var width = window.innerWidth
        if (breadcrumbsWrapper.hasClass("invisible") & width >= 1200) breadcrumbsWrapper.removeClass("invisible")
        else if (!breadcrumbsWrapper.hasClass("invisible") & width < 1200) breadcrumbsWrapper.addClass("invisible")
    })
})

function loadHeaderProperties() {
    var properties = getHeaderProperties()
    if (typeof properties.then === "function") properties.then(it => internalLoadHeaderProperties(it))
    else internalLoadHeaderProperties(properties)
}

function internalLoadHeaderProperties(properties) {
    headerColor = properties.color
    if ($document.scrollTop() != 0) $document.css({ "background-color": headerColor })
    properties.breadcrumbs.forEach(breadcrumb => {
        $(document.createElement("a")).addClass("breadcrumb").text(breadcrumb.name).click(() => loadPage(breadcrumb.path)).appendTo(breadcrumbsWrapper)
    })
    title.text(properties.title)
}

var hashValuesHandlers = {
    message: (value, hashValuesObject) => {
        var message = messageHandlers[value](hashValuesObject)
        M.toast({ html: message })
    }
}

var messageHandlers = {
    authenticated: (hashValuesObject) => {
        return "Authenticated successfully."
    }, errorRedirected: (hashValuesObject) => {
        return "That page couldn't be loaded so you were redirected to the home page.<br>" + hashValuesObject.code
    }
}

window.onpopstate = event => loadPage(event.state.page, true)

function loadPage(path, dontLoadPage) {
    breadcrumbsWrapper.empty()
    var mainPage = $(".mainPage").clone().appendTo(document.body).removeClass("mainPage").addClass("pageDissapearing")
    setTimeout(() => mainPage.remove(), 400)
    $(".mainPage").empty()
    footer.animate({ opacity: 0 })

    var loadSpinner = $(document.createElement("div")).addClass("loadSpinner").appendTo(loadSpinnerWrapper)
    for (i = 0; i < spinnerElements; i ++) loadSpinner.append($(document.createElement("div")))
    loadSpinner.doLoadSpinner().css({ opacity: 0 }).animate({ opacity: 1 }, 200)
    console.log(loadSpinner, loadSpinnerWrapper)

    $.ajax({
        url: path,
        headers: {
            "PageOnly": "true"
        },
        error: (jqXHR, textStatus, errorThrown) => {
            M.toast({ html: "Failed to go to that page!<br>" + textStatus + "<br>" + errorThrown + "<br>Redirected to home page." })
            loadPage("/")
        }
    }).done((data, jqXHR, status) => {
        breadcrumbsWrapper.empty()
        var mainPage = $(".mainPage")
        mainPage.append(data).ready(() => {
            if (typeof pageReady === "function") pageReady()
            loadHeaderProperties()            
            
            new CSSAnimation({ opacity: "0" }, { opacity: "1" }, 400).animate($(".mainPage"), pow5Out)
            footer.clearQueue().stop().animate({ opacity: 1 })
            loadSpinner.animate({ opacity: 0 }, 200, "linear", () => loadSpinner.remove())
        })

        if (!dontLoadPage) history.pushState({"page": path}, path, path)
    })
}

Number.prototype.clamp = function(min, max) {
    return Math.min(Math.max(this, min), max)
}

Number.prototype.lerp = function(from, to) {
    return from + this.clamp(0, 1) * (to - from);
}
  
var intplValue = 2, intplPower = 5,
    intplMin = Math.pow(intplValue, -intplPower), 
    intplScale = 1 / (1 - intplMin)
function pow5In(number) {
    return 1 - (Math.pow(intplValue, -intplPower * number) - intplMin) * intplScale
}
function pow5Out(number) {
    return Math.pow(number - 1, intplPower) * (intplPower % 2 == 0 ? -1 : 1) + 1
}

function CSSAnimation(fromCSS, toCSS, duration) {
    if (!duration) duration = 500
    var functionRegex = /\(.*\)/g, suffixes = [ "vw", "vh", "px", "%", "deg" ]
    this.options = []
    this.active = true
    this.duration = duration

    $.each(fromCSS, (key, value) => {
        var unfilteredTo = toCSS[key]

        function getSuffix(text) {
            for (suffixIndex in suffixes) {
                var suffix = suffixes[suffixIndex]
                if (text.endsWith(suffix)) return text.substring(text.length - suffix.length)
            }
            return ""
        }
        if (value.match(functionRegex)) {
            function mapFunction(suffixMap, arg) {
                var suffix = getSuffix(arg)
                suffixMap.push(suffix)
                return arg.substring(0, arg.length - suffix.length)
            }
            function getFunctionContents(text) {
                return text.substring(text.indexOf("(") + 1, text.length - 1).replace(" ", "")
            }

            var suffixesFrom = [], matchingFrom = getFunctionContents(value), argumentsFrom = matchingFrom.split(",").map(arg => mapFunction(suffixesFrom, arg)), 
                matchingTo = getFunctionContents(unfilteredTo), argumentsTo = matchingTo.split(",").map(arg => mapFunction([], arg)),
                cssgenArgs = []
            
            var index = 0
            argumentsFrom.forEach(argFrom => {
                cssgenArgs.push({ from: parseFloat(argFrom), to: parseFloat(argumentsTo[index]) })
                index ++
            })
            
            this.options.push({
                lerpValues: cssgenArgs, cssgen: output => {
                    var string = "(", index = 0
                    output.forEach(arg => {
                        if (index != 0) string += ", "
                        string += arg + suffixesFrom[index] 
                        index ++
                    })

                    return unfilteredTo.substring(0, unfilteredTo.indexOf("(")) + string + ")"
                }, key
            })
        } else {
            var suffixFrom = getSuffix(value), from = value.substring(0, value.length - suffixFrom.length), 
                suffixTo = getSuffix(unfilteredTo), to = unfilteredTo.substring(0, unfilteredTo.length - suffixTo.length)
                this.options.push({
                    lerpValues: [ 
                        { from: parseFloat(from), to: parseFloat(to) }
                    ], cssgen: output => output[0] + suffixFrom,
                key
            })
        }
    })
}

CSSAnimation.prototype.animate = function(element, smoothingFunction, after) {
    var begin = Date.now()
    if (!smoothingFunction) smoothingFunction = input => input // Linear
    if (element.animation) {
        element.animation.stop()
        element.animation = this
    }

    var step = (progress) => {
        var cssObject = {}
        this.options.forEach(option => cssObject[option.key] = option.cssgen(option.lerpValues.map(cur => progress.lerp(cur.from, cur.to))))
        element.css(cssObject)

        if (this.active) requestAnimationFrame(() => step(smoothingFunction((Date.now() - begin) / this.duration)))
    }
    step(0)

    setTimeout(() => {
        this.active = false
        step(1)
        if (this.after) after()
    }, this.duration);
}

CSSAnimation.prototype.stop = function() {
    this.active = false
}

function apiRequest(path, body) {
    return new Promise((resolve, reject) => {
        $.ajax({
            url: path,
            data: JSON.stringify(body),
            method: "POST",
            error: (jqXHR, textStatus, errorThrown) => {
                reject(errorThrown)
            }
        }).done((data, jqXHR, status) => {
            var json = JSON.parse(data)
            console.log("API request took " + json.msTaken + "ms.")
            if (json.success) resolve(json.response)
            else reject(json.error)
        })
    })
}

$.fn.doLoadSpinner = function (smallTime, bigTime, wait) {
    var spanProgress = [], spanArray = this.children(), spanAmount = spanArray.length, updateSmallTime = smallTime || 600, updateBigTime = bigTime || smallTime || 600, waitTime = wait || 50

    for (var i = 0; i < spanAmount; i ++) {
        spanProgress.push(i / spanAmount) 
    }

    var curAnimationData = {
        time: 0,
        begin: Date.now(),
        fromProgresses: spanProgress,
        toProgresses: spanProgress,
        fromRadius: 36,
        toRadius: 48
    }

    function setPositions(radius, animate) {
        var newCAD = {
            time: animate,
            begin: Date.now(),
            fromProgresses: curAnimationData.toProgresses,
            toProgresses: spanProgress,
            fromRadius: curAnimationData.toRadius,
            toRadius: radius
        }

        curAnimationData = newCAD
    }

    function animationFrame() {
        if (this && this.loadSpinnerTimeout) {
            var progress = pow5Out(((Date.now() - curAnimationData.begin) / curAnimationData.time)),
                lerpedRadius = progress.lerp(curAnimationData.fromRadius, curAnimationData.toRadius)
                i = 0
            spanArray.each(function() {
                var element = $(this),
                    fromProgress = curAnimationData.fromProgresses[i],
                    toProgress = curAnimationData.toProgresses[i],
                    lerped = progress.lerp(fromProgress, toProgress)

                var angleDegrees = lerped * 360,
                    angleRad = angleDegrees / 180 * Math.PI,
                    x = lerpedRadius * Math.cos(angleRad),
                    y = lerpedRadius * Math.sin(angleRad)
                
                element.css({
                    left: x - i * 3.5,
                    top: y
                })
                i ++
            })

            window.requestAnimationFrame(animationFrame)
        }
    }
    window.requestAnimationFrame(animationFrame)

    updateSmall()

    function updateSmall() {
        spanProgress = spanProgress.map(cur => cur + 0.15)
        setPositions(36, updateSmallTime)
        this.loadSpinnerTimeout = setTimeout(updateBig, updateSmallTime)
    }

    function updateBig() {
        spanProgress = spanProgress.map(cur => cur + 0.4)
        setPositions(48, updateBigTime)
        this.loadSpinnerTimeout = setTimeout(updateSmall, updateBigTime)
    }

    return this;
}

function setLang(lang) {
    apiRequest("/setlang", lang).then(nothing => location.reload())
}