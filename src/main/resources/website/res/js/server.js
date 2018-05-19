var vibrantSwatches = undefined, modalInstance = undefined, $body = undefined
function pageReady() {
    var backgroundBanner = $(".backgroundBanner"), topMargin = $(".beforeRankings"), backgroundBannerIcon = $(".backgroundBannerIcon")
    vibrantSwatches = new Vibrant($(".backgroundBannerIcon").get(0))
    backgroundBanner.css({ height: topMargin.offset().top - 5 })
    $body = $("body")
}

function getHeaderProperties() {
    var rgb = (vibrantSwatches.VibrantSwatch || { rgb: [ 183, 28, 28 ] }).rgb
    return {
        breadcrumbs: [
            { name: "GamesROB", path: "/" },
            { name: guildName, path: window.location.href }
        ], color: `rgb(${rgb[0]}, ${rgb[1]}, ${rgb[2]})`,
        title: guildName + " GamesROB Leaderboard"
    }
}

function openModalUser(id) {
    apiRequest("userProfileModal", { id }).then(modal => {
        $(".modalContainer").html(modal).ready(() => {
            var $modalEl = $("#userProfileModal"), modalEl = $modalEl.get(0)
            M.Modal.init(modalEl)
            M.Modal.getInstance(modalEl).open()
        })
    }).catch(e => {
        console.log(e)
        M.toast({ html: "Couldn't load user profile pop up!<br>" + e })
    })
}