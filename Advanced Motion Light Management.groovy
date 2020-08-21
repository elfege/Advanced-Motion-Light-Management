/*
*  Copyright 2016 elfege
*
*    Software distributed under the License is distributed on an "AS IS" BASIS, 
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
*    for the specific language governing permissions and limitations under the License.
*
*    Light / motion Management
*
*  Author: Elfege
*/

import java.text.SimpleDateFormat
import groovy.transform.Field
import groovy.json.JsonOutput


@Field static int delays = 0


definition(
    name: "Advanced Motion Lighting Management",
    namespace: "elfege",
    author: "elfege",
    description: "Switch light with motion events",
    category: "Convenience",
    iconUrl: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX2Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
    iconX3Url: "http://static1.squarespace.com/static/5751f711d51cd45f35ec6b77/t/59c561cb268b9638e8ba6c23/1512332763339/?format=1500w",
)

preferences {

    page name:"pageSetup"

}
def pageSetup() {

    boolean haveDim = false

    if(atomicState.paused)
    {
        log.debug "new app label: ${app.label}"
        while(app.label.contains(" (Paused) "))
        {
            app.updateLabel(app.label.minus("(Paused)" ))
        }
        app.updateLabel(app.label + ("<font color = 'red'> (Paused) </font>" ))
    }
    else if(app.label.contains("(Paused)"))
    {
        app.updateLabel(app.label.minus("<font color = 'red'> (Paused) </font>" ))
        while(app.label.contains(" (Paused) ")){app.updateLabel(app.label.minus("(Paused)" ))}
        log.debug "new app label: ${app.label}"
    }


    def pageProperties = [
        name:       "pageSetup",
        title:      "${app.label}",
        nextPage:   null,
        install:    true,
        uninstall:  true
    ]

    return dynamicPage(pageProperties) {
        if(atomicState.paused == true)
        {
            atomicState.button_name = "resume"
            logging("button name is: $atomicState.button_name")
        }
        else 
        {
            atomicState.button_name = "pause"
            logging("button name is: $atomicState.button_name")
        }
        section("")
        {
            input "pause", "button", title: "$atomicState.button_name"

        }


        section("motion sensors")
        {
            input "motionSensors", "capability.motionSensor", title: "Choose your motion sensors", despcription: "pick a motion sensor", required:true,
                multiple:true, submitOnChange: true
        }   
        section("Switches")
        {
            input "switches", "capability.switch", title: "Control this light", required: true, multiple:true, description:"Select a switch", submitOnChange:true   
        }
        section("Timing")
        {

            input "timeUnit", "enum", title:"Timeout Time Unit ?", description:"select your prefered unit of time", defaultValue:"seconds", options:["seconds", "minutes"], submitOnChange:true

            if(timeUnit == null){app.updateSetting("timeUnit",[value:"seconds", type:"enum"])}

            input "timeWithMode", "bool", title: "Timeout with modes", defaultValue:false, submitOnChange: true
            if(timeWithMode)
            {
                input "timeModes", "mode", title: "Select modes", multiple: true, submitOnChange: true
                if(timeModes){
                    def i = 0
                    atomicState.dimValMode = []
                    def dimValMode = []
                    for(timeModes.size() != 0; i < timeModes.size(); i++){
                        input "noMotionTime${i}", "number", required:true, title: "select a timeout value for ${timeModes[i]}"
                    }
                }
            }
            else {
                input "noMotionTime", "number", title: "turn light off after how long?", required: true, description:"Enter a value in $timeUnit"
            }
        }
        section("contact sensors")
        {
            input "contacts", "capability.contactSensor", title: "Use contact sensors to trigger these lights", multiple:true, required: false, submitOnChange: true 
            def listDim = switches.findAll{it.hasCapability("Switch Level")}
            log.debug "list of devices with dimming capability = $listDim"
            haveDim = listDim.size()>0
            descriptiontext ("dimmer capability?:$haveDim")
            if(haveDim)
            {
                input "useDim", "bool", title:"Use ${listDim.toString()} dimming capabilities", submitOnChange:true   
            }
            if(contacts && useDim)
            {
                input "dimValClosed", "number", title: "Desired value when contacts are closed", required:true
                input "dimValOpen"  , "number", title: "Desired value when contacts are open", required: true
                input "contactModes", "bool", title: "Use this option only if location is in specific modes", defaultValue: false, submitOnChange:true
                if(contactModes)
                {
                    input "modesForContacts", "mode", title: "Select modes", multiple: true, submitOnChange: true
                }
            }

        }

        section()
        {
            def cap = switches.findAll{it.hasCapability("SwitchLevel")}

            if(cap.size()>0)
            {
                descriptiontext "Dimmer capability detected for $cap"
                input "dimmers", "capability.setLevel", title: "select dimmers", required: false  
            }
        }

        section("modes")        
        {
            input "restrictedModes", "mode", title:"Pause this app if location is in one of these modes", required: false, multiple: true
        }

        section() {
            label title: "Assign a name", required: false
        }
        section("logging")
        {
            input "enablelogging", "bool", title:"Enable logging", value:false, submitOnChange:true
            input "enabledescriptiontext", "bool", title:"Enable description text", value:false, submitOnChange:true
        }
        section("Watchdog")
        {
            input "watchdog", "bool", title: "Run the watchdog (reboot your hub when it takes too long to respond to events and device commands)", defaultValue: false, submitOnChange:true 
            if(watchdog)
            {
                input "ip", "text", title: "Type the IP address of your hub"
            }
        }
        section()
        {
            if(atomicState.installed)
            {
                input "update", "button", title: "UPDATE"
                input "run", "button", title: "RUN"
            }
        }
    }
}

def installed() {
    logging("Installed with settings: ${settings}")

    atomicState.lastReboot = now()
    atomicState.installed = true
    initialize()

}
def updated() {
    descriptiontext "updated with settings: ${settings}"
    atomicState.installed = true        
    atomicState.fix = 0
    unsubscribe()
    unschedule()
    initialize()
}
def initialize() {

    if(enablelogging == true){
        atomicState.EnableDebugTime = now()
        runIn(1800, disablelogging)
        descriptiontext "disablelogging scheduled to run in ${1800/60} minutes"
    }
    else 
    {
        log.warn "debug logging disabled!"
    }

    subscribe(motionSensors, "motion", mainHandler)
    log.trace "${motionSensors} subscribed to mainHandler"

    if(watchdog)
    {
        subscribe(motionSensors, "motion", motionHandler)
        log.trace "${motionSensors} subscribed to - motionHandler -"
    }


    i = 0
    s = switches.size()
    for(s!=0;i<s;i++)
    {
        subscribe(switches[i], "switch", switchHandler)
        log.trace "${switches[i]} subscribed to switchHandler"
    }
    if(contacts)
    {
        i = 0
        s = contacts.size()
        for(s!=0;i<s;i++)
        {
            subscribe(contacts[i], "contact", mainHandler)
            log.trace "${contacts[i]} subscribed to mainHandler"
        }
    }

    subscribe(location, "systemStart", hubEventHandler) // manage bugs and hub crashes

    subscribe(modes, "mode", locationModeChangeHandler)
    atomicState.timer = 1
    schedule("0 0/${atomicState.timer} * * * ?", master) 
    atomicState.lastRun = now() as long // time stamp to see if cron service is working properly
        logging("initialization done")
    //master()
}

def appButtonHandler(btn) {

    switch(btn) {
        case "pause":atomicState.paused = !atomicState.paused
        log.debug "atomicState.paused = $atomicState.paused"
        if(atomicState.paused)
        {
            log.debug "unsuscribing from events..."
            unsubscribe()  
            log.debug "unschedule()..."
            unschedule()
            break
        }
        else
        {
            updated()            
            break
        }
        case "update":
        atomicState.paused = false
        updated()
        break
        case "run":
        if(!atomicState.paused)  
        {
            master()
            dim()
        }
        else
        {
            log.warn "App is paused!"
        }
        break

    }
}
def switchHandler(evt){
    descriptiontext "$evt.device is $evt.value"


    if(evt.value == "on")
    {
        atomicState.motionEventTime = atomicState.motionEventTime != null ? atomicState.motionEventTime : now()
        atomicState.thisIsAMotionEvent = atomicState.thisIsAMotionEvent != null ? atomicState.thisIsAMotionEvent : false
        atomicState.wtcEvts = atomicState.wtcEvts != null ? atomicState.wtcEvts : 0

        int tolerance = 4000
        int critical = 15000
        long elapsed = now() - atomicState.motionEventTime // measure the elapsed time between last active motion event and switch event
        int limit = 5 // after 5 occurences in a row, reboot
        boolean justRestarted = now() - atomicState.lastReboot < 120000

        if(atomicState.thisIsAMotionEvent)
        {
            if(!justRestarted)
            {
                if(elapsed > tolerance && atomicState.cmdFromApp)
                {
                    atomicState.wtcEvts += 1
                    def message = elapsed > critical ? "CRITICAL DELAY (${elapsed/1000}seconds), REBOOTING..." : "$app.label took ${elapsed/1000} seconds to execute (occurence #${atomicState.wtcEvts})"
                    log.warn message

                    if(atomicState.wtcEvts > limit || elapsed > critical)
                    {
                        reboot()
                    }
                }
                else 
                {
                    atomicState.wtcEvts = 0
                    log.trace "Watchdog OK"
                }

            }
            else 
            {
                log.warn "Hub has restarted less than 2 minutes ago, watchdog eval skipped"
            }

        }
        else 
        {
            log.warn "Switch event other than motion triggered, watchdog eval skipped"
        }            
    }


    atomicState.thisIsAMotionEvent = false
    atomicState.cmdFromApp = false

}
def locationModeChangeHandler(evt){
    logging("$evt.name is now in $evt.value mode")   
}
def mainHandler(evt){

    if(location.mode in restrictedModes)
    {
        descriptiontext "location in restricted mode, doing nothing"
        return
    }

    descriptiontext "${evt.name}: $evt.device is $evt.value"

    if(evt.value in ["open", "active"]) 
    {
        switches.on() // bypass the on() method for shorter response time
    }
    else 
    {
        master()
    }

}
def motionHandler(evt){
    descriptiontext "$evt.device is $evt.value"

    if(evt.value == "active")
    {
        atomicState.motionEventTime = now()
        atomicState.thisIsAMotionEvent = true // to distinguis from routine run using StillActive collection (avoids false watchdog positives)
        on() // to prevent watchdog false positives
    }
}
def hubEventHandler(evt){

    if(location.mode in restrictedModes)
    {
        logging("App paused due to modes restrictions")
        return
    }
    log.warn "HUB $evt.name"
    if(evt.name == "systemStart")
    {
        log.warn "reset atomicState.lastReboot = now()"
        atomicState.lastReboot = now()

        updated()
    }
}


def master(){

    if(location.mode in restrictedModes)
    {
        logging("App paused due to modes restrictions")
        return
    }

    if(!stillActive())
    {
        off()  
    }
    else 
    {
        on() 

    }

    if(enabledebug && now() - atomicState.EnableDebugTime > 1800000)
    {
        descriptiontext "Debug has been up for too long..."
        disablelogging() 
    }

    atomicState.lastRun = now() // time stamp to see if cron service is working properly
    logging("END")
}

def reboot(){
    runCmd(ip, "8080", "/hub/reboot")// reboot
}
def timeout(){
    def result = noMotionTime // default
    if(timeWithMode)
    {
        int i = 0
        while(location.mode != timeModes[i]){i++}
        valMode = "noMotionTime${i}"
        valMode = settings.find{it.key == valMode}?.value
        logging("returning value for ${timeModes[i]}: $valMode ${timeUnit}")
        result = valMode
    }
    if(result == null)
    {
        return noMotiontime
    }
    logging("timeout() returns $result")
    return result
}

def dim(){
    if(useDim && contacts){
        boolean closed = !contactsAreOpen()
        def switchesWithDimCap = switches.findAll{it.hasCapability("SwitchLevel")}
        log.debug "list of devices with dimming capability = $listDim"
        
        log.info "switchesWithDimCap = $switchesWithDimCap"

        int i = 0
        int s = switchesWithDimCap.size()

        if(closed)
        {
            if(WrongLevel(dimValClosed)){

                for(s!=0;i<s;i++)
                {
                    switchesWithDimCap[i].setLevel(dimValClosed)
                    logging("${switchesWithDimCap[i]} set to $dimValClosed 9zaeth")
                }
            }
        }
        else
        {
            if(!contactModeOk()) // ignore that location is not in the contact mode and dim to dimValClosed
            {
                if(WrongLevel(dimValClosed)){

                    for(s!=0;i<s;i++)
                    {
                        switchesWithDimCap[i].setLevel(dimValClosed)
                        logging("${switchesWithDimCap[i]} set to $dimValClosed 78fr")
                    }
                }
            }
            else 
            {
                if(WrongLevel(dimValOpen)){
                    for(s!=0;i<s;i++)
                    {
                        switchesWithDimCap[i].setLevel(dimValOpen)
                        logging("${switchesWithDimCap[i]} set to $dimValOpen 54fre")
                    }
                }
            }
        }
    }
}
boolean WrongLevel(requiredLevel){    // if any returns the wrong level, then return true
    return switches.findAll{it.currentValue("level") != requiredLevel} 
}
boolean contactModeOk(){
    boolean result = true
    if(contacts && contactModes)
    {
        if(location.mode in modesForContacts)
        {
            return true
        }
        else 
        {
            return false
        }
    }
    return result
}
boolean contactsAreOpen(){
    def s = contacts.size()
    def i = 0

    def openList = contacts.findAll{it.currentValue("contact") == "open"}

    logging("Currently Open Contacts $openList")
    return openList.size() > 0
}
boolean stillActive(){

    int noMotionTime = timeout()
    long Dtime = noMotionTime * 1000 
    def unit = "seconds"
    if(timeUnit == "minutes")
    {
        logging "time unit is minutes"
        Dtime = noMotionTime * 1000 * 60
    }


    int s = motionSensors.size() 
    int i = 0
    def thisDeviceEvents = []
    int events = 0
    boolean AnyCurrentlyActive = motionSensors.findAll{it.currentValue("motion") == "active"}?.size() != 0
    if(AnyCurrentlyActive) descriptiontext "AnyCurrentlyActive = $AnyCurrentlyActive"
    if(AnyCurrentlyActive) return true  // for faster execution when true

    for(s != 0; i < s; i++) // collect active events
    { 
        thisDeviceEvents = motionSensors[i].eventsSince(new Date(now() - Dtime)).findAll{it.value == "active"} // collect motion events for each sensor separately
        events += thisDeviceEvents.size() 

    }

    descriptiontext("$events active events in the last $noMotionTime $timeUnit")
    return events > 0 || AnyCurrentlyActive
}

def off(){

    def anyOn = switches.any{it -> it.currentValue("switch") == "on" }//.size() > 0
    descriptiontext "anyOn = $anyOn"
    if(anyOn){
        descriptiontext "turning off $switches"
        switches.off()
    }
    else 
    {
        descriptiontext "$switches already off"
    }

}
def on(){
    atomicState.cmdFromApp = true
    boolean anyOff = switches.any{it -> it.currentValue("switch") == "off"}//.size() > 0
    descriptiontext "anyOff = $anyOff"
    if(anyOff){
        switches.on()
        descriptiontext "$switches turned on"
    } 
    else 
    {
        descriptiontext "$switches already on"
    }

    dim()
}

def runCmd(String ip,String port,String path) {

    def uri = "http://${ip}${":"}${port}${path}"
    log.debug "POST: $uri"

    def reqParams = [
        uri: uri
    ]

    try {
        httpPost(reqParams){response ->
        }
    } catch (Exception e) {
        log.error "${e}"
    }
}
def logging(msg){
    //log.warn "enablelogging ? $enablelogging" 
    if (enablelogging) log.debug msg
    if(debug && atomicState.EnableDebugTime == null) atomicState.EnableDebugTime = now()
}
def descriptiontext(msg){
    //log.warn "enabledescriptiontext = ${enabledescriptiontext}" 
    if (enabledescriptiontext) log.info msg
}
def disablelogging(){
    app.updateSetting("enablelogging",[value:"false",type:"bool"])
    log.warn "logging disabled!"
}


