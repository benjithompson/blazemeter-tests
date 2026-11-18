require('dotenv').config();
const assert = require('assert');
const { remote } = require('webdriverio');
let driver;

// Perfecto Cloud configuration
const PERFECTO_HOST = 'demo.perfectomobile.com'; // Replace with your Perfecto cloud URL
const PERFECTO_SECURITY_TOKEN = process.env.PERFECTO_SECURITY_TOKEN || 'YOUR_SECURITY_TOKEN_HERE';

var capabilities = {
    'platformName': 'iOS',
    'browserName': 'Safari',
    'appium:automationName': 'XCUITest',
    'perfecto:options': {
        'securityToken': PERFECTO_SECURITY_TOKEN,
        'deviceName': '00008130-001839960C10001C'
    },
}

describe('Blazedemo verification', function() {
    this.timeout(60000); // Increased timeout for cloud execution
 
    before(async () => {
        // WebDriverIO connection to Perfecto Cloud
        driver = await remote({
            protocol: 'https',
            hostname: PERFECTO_HOST,
            port: 443,
            path: '/nexperience/perfectomobile/wd/hub',
            capabilities: capabilities,
            logLevel: 'info'
        });
        
        // Navigate to demo sites
        await driver.url('https://www.blazedemo.com/');
        await driver.pause(2000); // Wait for page to load
        await driver.url('https://www.demoblaze.com/');
        await driver.pause(2000); // Wait for page to load
    });
 
    after(async function () {
        if (driver) {
            await driver.deleteSession();
        }
    });
    
    it('should load BlazeDemo successfully', async function() {
        await driver.url('https://www.blazedemo.com/');
        const title = await driver.getTitle();
        assert(title.includes('BlazeDemo'), `Expected title to contain 'BlazeDemo', but got: ${title}`);
    });
    
    it('should load DemoBlaze successfully', async function() {
        await driver.url('https://www.demoblaze.com/');
        const title = await driver.getTitle();
        assert(title.includes('STORE'), `Expected title to contain 'STORE', but got: ${title}`);
    });
});