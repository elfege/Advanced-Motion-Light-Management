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

@Field static boolean cmdByApp = false
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

    if(state.paused)
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
        if(state.paused == true)
        {
            state.button_name = "resume"
            logging("button name is: $state.button_name")
        }
        else 
        {
            state.button_name = "pause"
            logging("button name is: $state.button_name")
        }
        section("")
        {
            input "pause", "button", title: "$state.button_name"

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
                    state.dimValMode = []
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
            def cap = switches.findAll{it.hasCapability("dimmer")}

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
            input "descriptiontext", "bool", title:"Enable description text", value:false, submitOnChange:true

        }
        section()
        {
            if(state.installed)
            {
                input "update", "button", title: "UPDATE"
                input "run", "button", title: "RUN"
            }
        }
    }
}



def installed() {
    logging("Installed with settings: ${settings}")

    state.lastReboot = now()
    state.installed = true
    initialize()

}
def updated() {
    descriptiontext "updated with settings: ${settings}"
    state.installed = true        
    state.fix = 0
    unsubscribe()
    unschedule()
    initialize()
}

def initialize() {
    
    state.activeEvtTime = now()            

    if(enablelogging == true){
        state.EnableDebugTime = now()
        runIn(1800, disablelogging)
        descriptiontext "disablelogging scheduled to run in ${1800/60} minutes"
    }
    else 
    {
        log.warn "debug logging disabled!"
    }

    int i = 0
    int s = 0

    i = 0
    s = motionSensors.size()
    for(s!=0;i<s;i++)
    {
        subscribe(motionSensors[i], "motion", mainHandler)
        log.trace "${motionSensors[i]} subscribed to mainHandler"
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
    state.timer = 1
    schedule("0 0/${state.timer} * * * ?", master) 
    state.lastRun = now() as long // time stamp to see if cron service is working properly
        logging("initialization done")
    //master()
}

def switchHandler(evt)
{
    logging "$evt.device is $evt.value Command came from this app: $cmdByApp"

    cmdByApp = false
}
def locationModeChangeHandler(evt){
    logging("$evt.name is now in $evt.value mode")   
}
def mainHandler(evt){

    descriptiontext "${evt.name}: $evt.device is $evt.value"

    if(location.mode in restrictedModes)
    {
        descriptiontext "location in restricted mode, doing nothing"
    }
    else 
    {
        if(evt.name == "motion" && evt.value == "active")
        {
            state.activeEvtTime = now()
        }
        master()
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
        log.warn "reset state.lastReboot = now()"
        state.lastReboot = now()

        updated()
    }
}
def appButtonHandler(btn) {

    if(location.mode in restrictedModes)
    {
        logging("App paused due to modes restrictions")
        return
    }
    switch(btn) {
        case "pause":state.paused = !state.paused
        log.debug "state.paused = $state.paused"
        if(state.paused)
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
        state.paused = false
        updated()
        break
        case "run":
        if(!state.paused)  
        {
            master()
        }
        else
        {
            log.warn "App is paused!"
        }
        break

    }
}

def master(){

    if(location.mode in restrictedModes)
    {
        logging("App paused due to modes restrictions")
        return
    }
    logging("**********${new Date().format("h:mm:ss a", location.timeZone)}****************")
    logging("Restricted modes are: $restrictedModes")

    if(location.mode in restrictedModes)
    {
        descriptiontext("location in restricted mode, doing nothing")
        return
    }
    else 
    {
        if(!stillActive())
        {
            off()
        }
        else 
        {
            on()
        }

        if(enabledebug && now() - state.EnableDebugTime > 1800000)
        {
            descriptiontext "Debug has been up for too long..."
            disablelogging() 
        }
    }
    state.lastRun = now() // time stamp to see if cron service is working properly
    logging("END")
}

def reboot(){
    runCmd("192.168.10.70", "8080", "/hub/reboot")// reboot
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
def off(){

    boolean anyOn = switches.findAll{it.currentValue("switch") == "on"}
    logging "anyOn = $anyOn"
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

    cmdByApp = true

    if(useDim && contacts)
    {
        dim()
    }
    else
    {
        boolean anyOff = switches.findAll{it.currentValue("switch") == "off"}
        logging "anyOff = $anyOff"
        if(anyOff){
            switches.on()
            descriptiontext "$switches turned on"
        } 
        else 
        {
            descriptiontext "$switches already on"
        }
    }

}


def dim(){
    boolean closed = !contactsAreOpen()
    if(closed)
    {
        switches.setLevel(dimValClosed)
        logging("$switches set to $dimValClosed 9zaeth")
    }
    else
    {
        if(!contactModeOk()) // ignore if we are not in the contact mode and dim to dimValClosed
        {
            switches.setLevel(dimValClosed)
            logging("$switches set to $dimValClosed 78fr")
        }
        else 
        {
            switches.setLevel(dimValOpen)
            logging("$switches set to $dimValClosed 54fre")
        }
    }
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
    def currentlyActive = motionSensors.findAll{it.currentValue("motion") == "active"}
    boolean AnyCurrentlyActive = currentlyActive.size() != 0
    //log.warn "AnyCurrentlyActive: $AnyCurrentlyActive"

    for(s != 0; i < s; i++) // collect active events
    { 
        thisDeviceEvents = motionSensors[i].eventsSince(new Date(now() - Dtime)).findAll{it.value == "active"} // collect motion events for each sensor separately
        events += thisDeviceEvents.size() 

    }

    descriptiontext("$events active events in the last $noMotionTime $timeUnit")
    return events > 0 || AnyCurrentlyActive
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
    if (enablelogging) log.debug msg
    if(debug && state.EnableDebugTime == null) state.EnableDebugTime = now()
}

def descriptiontext(msg){
    //log.warn "descriptiontext = $descriptionText"    
    if (descriptiontext) log.info msg
}

def disablelogging(){
    app.updateSetting("enablelogging",[value:"false",type:"bool"])
    log.warn "logging disabled!"
}
