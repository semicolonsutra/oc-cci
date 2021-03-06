#!/usr/bin/python -u
# -u for unbuffered

import sys
from pmonitor import PMonitor
from daemon import Daemon
################################################################################
sensors = ['SEAWIFS', 'MODIS']

gains = {
    'MODIS':
        {
            'NOVIC' : 'CALIB        1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0',
            'VIC1' : 'CALIB        0.997 1.000 1. 0.999 1.001 0.999 1. 1. 0.993 1. 1. 1. 1. 1.',
            'VIC2' : 'CALIB        1. 0.998 1. 0.997 0.999 0.998 1. 1. 0.994 1. 1. 1. 1. 1.',
        },
    'SEAWIFS':
        {
            'NOVIC' : 'CALIB        1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0',
            'bandset_1' : 'CALIB 1. 1.004 0.999 1.000 1.011 1. 1. 1.',
            'bandset_2' : 'CALIB 1. 1.004 0.998 0.999 1.009 0.980 1. 1.',
            'bandset_3' : 'CALIB 1.043 1.028 1.012 1.010 1. 1. 1. 1.',
            'bandset_4' : 'CALIB 0.994 0.991 0.991 0.996 1.011 0.993 1. 1.',
          }
}

ftpDirs = {
    'MODIS':
        {
            'NOVIC' : 'VC/Gain_validation_2014-11-05/MODIS/NOVIC',
            'VIC1' : 'VC/Gain_validation_2014-11-05/MODIS/bandset_1',
            'VIC2' : 'VC/Gain_validation_2014-11-05/MODIS/bandset_2',
        },
    'SEAWIFS':
        {
            'NOVIC' : 'VC/Gain_validation_2014-11-05/SeaWiFS/NOVIC',
            'bandset_1' : 'VC/Gain_validation_2014-11-05/SeaWiFS/bandset_1',
            'bandset_2' : 'VC/Gain_validation_2014-11-05/SeaWiFS/bandset_2',
            'bandset_3' : 'VC/Gain_validation_2014-11-05/SeaWiFS/bandset_3',
            'bandset_4' : 'VC/Gain_validation_2014-11-05/SeaWiFS/bandset_4',
          }
}

pointData = {
    'MODIS': 'oc_cci_v2.2_bs_MODIS_allsites.csv',
    'SEAWIFS':  'oc_cci_v2.2_bs_SeaWiFS_allsites.csv',
}
################################################################################
calvalusPointDataRoot = '/calvalus/projects/vc-analysis/point-data'
localPointDataRoot = 'vc-ana-point-data'

hosts = [('localhost', 1)]
types = [('ingest-point-data.sh', 1), ('template-step.py', 1)]
################################################################################
class MyDeamon(Daemon):
    def run(self):
        allLocalPointData = []
        for sensor in sensors:
            allLocalPointData.append(localPointDataRoot + "/" + pointData[sensor])

        pm = PMonitor(allLocalPointData, request='ma_vc-analysis-ana', logdir='log', hosts=hosts, types=types)

        for sensor in sensors:
            localPointData = localPointDataRoot + "/" + pointData[sensor]
            calvalusPointData = calvalusPointDataRoot + '/' + pointData[sensor]
            handle = "ingest-point-data-" + sensor
            pm.execute('ingest-point-data.sh', [localPointData], [calvalusPointData], logprefix=handle)
            #======================================================
            for vc in gains[sensor].keys():
                params = ['ma_vc-analysis-ana-' + sensor + '-\${station}-\${vc}.xml',
                          'station', 'global',
                          'sensor', sensor,
                          'vc', vc,
                          'calib', '"'+gains[sensor][vc]+'"',
                          'calvalusPointData', calvalusPointData,
                          'output', '/calvalus/projects/vc-analysis3/' + sensor + "-" + vc
                ]
                handleMA = 'vc-analysis-ana-' + sensor + "-" + vc
                pm.execute('template-step.py', [calvalusPointData], [handleMA], parameters=params, logprefix=handleMA)

                params = ['/calvalus/projects/vc-analysis3/' + sensor + "-" + vc,
                          'vc-analysis-' + sensor + "-" + vc
                ]
                handleCP = 'vc-analysis-ana-' + sensor + "-" + vc + '-copy'
                pm.execute('copyMaResultsToLocal.sh', [handleMA], [handleCP], parameters=params, logprefix=handleCP)

                params = ['ftp.brockmann-consult.de',
                          'oc-cci',
                          'vc-analysis-' + sensor + "-" + vc + '.tar.gz',
                          ftpDirs[sensor][vc]
                ]
                handleFTP = 'vc-analysis-ana-' + sensor + "-" + vc + '-ftp'
                pm.execute('putOnFTP.sh', [handleCP], [handleFTP], parameters=params, logprefix=handleFTP)
            #======================================================
            params = ['ma_vc-analysis-ana-idepix-' + sensor + '.xml',
                          'calvalusPointData', calvalusPointData,
                          'output', '/calvalus/projects/vc-analysis3/' + sensor + "-idepix"
            ]
            handleMA = 'vc-analysis-ana-idepix-' + sensor
            pm.execute('template-step.py', [calvalusPointData], [handleMA], parameters=params, logprefix=handleMA)

            params = ['/calvalus/projects/vc-analysis3/' + sensor + "-idepix",
                      'vc-analysis-' + sensor + "-idepix"
                    ]
            handleCP = 'vc-analysis-ana-idepix-' + sensor + '-copy'
            pm.execute('copyMaResultsToLocal.sh', [handleMA], [handleCP], parameters=params, logprefix=handleCP)

            params = ['ftp.brockmann-consult.de',
                      'oc-cci',
                      'vc-analysis-' + sensor + '-idepix.tar.gz',
                      'VC/Gain_validation_2014-11-05'
            ]
            handleFTP = 'vc-analysis-ana-' + sensor + '-ftp'
            pm.execute('putOnFTP.sh', [handleCP], [handleFTP], parameters=params, logprefix=handleFTP)
        #======================================================
        pm.wait_for_completion()
        #======================================================
daemon = MyDeamon.setup(sys.argv)
